package com.tecsisa.streams.cassandra

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, ReceiveTimeout}
import com.datastax.driver.core.ResultSet
import com.tecsisa.streams.cassandra.BatchActor.{ExecutionFailed, RetryExecution}
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.batch.{BatchQuery, BatchType}
import com.websudos.phantom.builder.query.{ExecutableStatement, UsingPart}
import com.websudos.phantom.dsl._
import org.reactivestreams.{Subscriber, Subscription}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
 * The [[Subscriber]] internal implementation based on
 * Akka actors.
 *
 * @see [[com.tecsisa.streams.cassandra.ReactiveCassandra.StreamedCassandraTable.subscriber()]]
 */
class BatchSubscriber[CT <: CassandraTable[CT, T], T] private[cassandra] (
    table: CT,
    builder: RequestBuilder[CT, T],
    batchSize: Int,
    concurrentRequests: Int,
    batchType: BatchType,
    flushInterval: Option[FiniteDuration],
    completionFn: () => Unit,
    errorFn: Throwable => Unit,
    maxRetries: Int)(implicit system: ActorSystem, session: Session, space: KeySpace, ev: Manifest[T]) extends Subscriber[T] {

  private var actor: ActorRef = _

  override def onSubscribe(s: Subscription): Unit = {
    if (s == null) throw new NullPointerException()
    if (actor == null) {
      actor = system.actorOf(
        Props(
          new BatchActorImpl(
            table,
            builder,
            s,
            batchSize,
            concurrentRequests,
            batchType,
            flushInterval,
            completionFn,
            errorFn,
            maxRetries
          )
        )
      )
      s.request(batchSize * concurrentRequests)
    } else {
      // rule 2.5, must cancel subscription as onSubscribe has been invoked twice
      // https://github.com/reactive-streams/reactive-streams-jvm#2.5
      s.cancel()
    }
  }

  override def onNext(t: T): Unit = {
    if (t == null) throw new NullPointerException("On next should not be called until onSubscribe has returned")
    actor ! t
  }

  override def onError(t: Throwable): Unit = {
    if (t == null) throw new NullPointerException()
    actor ! t
  }

  override def onComplete(): Unit = {
    actor ! BatchActor.Completed
  }

}

object BatchActor {
  case object Completed
  case class RetryExecution[T](elements: ArrayBuffer[T], currentRetry: Int)
  case class ExecutionFailed[T](throwable: Throwable, elements: ArrayBuffer[T], currentRetry: Int)
}

abstract class BatchActor[CT <: CassandraTable[CT, T], T](
    table: CT,
    builder: RequestBuilder[CT, T],
    subscription: Subscription,
    batchSize: Int,
    concurrentRequests: Int,
    batchType: BatchType,
    flushInterval: Option[FiniteDuration],
    completionFn: () => Unit,
    errorFn: Throwable => Unit,
    maxRetries: Int)(implicit session: Session, space: KeySpace, ev: Manifest[T]) extends Actor with ActorLogging {

  import context.{dispatcher, system}

  private var buffer: ArrayBuffer[T] = _

  private var completed = false

  /** Total number of batches sent and not acknowledged yet by Cassandra */
  private var pendingBatches: Int = 0

  private val BaseRetryDelay = 5.seconds

  def query(elements: ArrayBuffer[T]): Future[ResultSet]

  /** If a flushInterval is provided, then set a ReceiveTimeout */
  flushInterval.foreach { interval =>
    context.setReceiveTimeout(interval)
  }

  initializeBuffer()

  def receive = {
    case ExecutionFailed(throwable, elements, currentRetry) =>
      // If the current retry is below the maximum, schedule retry execution
      if (currentRetry < maxRetries) {
        val nextRetry = currentRetry + 1
        val retryDelay = BaseRetryDelay * nextRetry
        log.warning(s"Retrying C* batch operation in $retryDelay. Current retry is $nextRetry out of $maxRetries")
        system.scheduler.scheduleOnce(retryDelay, self, RetryExecution(elements, nextRetry))
      } else {
        handleError(throwable)
      }

    case BatchActor.Completed =>
      if (buffer.nonEmpty) {
        executeStatements(buffer)
        initializeBuffer()
        pendingBatches += 1
      }
      completed = true

    case ReceiveTimeout =>
      if (buffer.nonEmpty) {
        executeStatements(buffer)
        initializeBuffer()
        pendingBatches += 1
      }

    case r: RetryExecution[T] =>
      executeStatements(r.elements, r.currentRetry)

    case rs: ResultSet =>
      pendingBatches -= 1
      if (completed && pendingBatches == 0) shutdown()
      if (!completed) subscription.request(batchSize)

    case t: T =>
      buffer.append(t)
      if (buffer.size == batchSize) {
        executeStatements(buffer)
        initializeBuffer()
        pendingBatches += 1
      }

    case t: Throwable =>
      handleError(t)
  }

  private def shutdown(): Unit = {
    completionFn()
    context.stop(self)
  }

  private def handleError(t: Throwable): Unit = {
    subscription.cancel()
    errorFn(t)
    buffer.clear()
    context.stop(self)
  }

  private def executeStatements(elements: ArrayBuffer[T], currentRetry: Int = 0): Unit = {
    query(elements).onComplete {
      case Failure(e) => self ! ExecutionFailed(e, elements, currentRetry)
      case Success(resp) => self ! resp
    }
  }

  private def initializeBuffer(): Unit = {
    buffer = new ArrayBuffer[T]()
    buffer.sizeHint(batchSize)
  }

}

class BatchActorImpl[CT <: CassandraTable[CT, T], T](
  table: CT,
  builder: RequestBuilder[CT, T],
  subscription: Subscription,
  batchSize: Int,
  concurrentRequests: Int,
  batchType: BatchType,
  flushInterval: Option[FiniteDuration],
  completionFn: () => Unit,
  errorFn: Throwable => Unit,
  maxRetries: Int)(implicit session: Session, space: KeySpace, ev: Manifest[T])
    extends BatchActor(
      table,
      builder,
      subscription,
      batchSize,
      concurrentRequests,
      batchType,
      flushInterval,
      completionFn,
      errorFn,
      maxRetries) {
  override def query(elements: ArrayBuffer[T]): Future[ResultSet] = new BatchQuery(
    elements.map(builder.request(table, _).qb).toIterator,
    batchType,
    UsingPart.empty,
    false,
    None
  ).future()

}

/**
 * This is the typeclass that should be implemented for a
 * given instance of T. Every implementation of this typeclass
 * should be provided implicitly in the scope in order to be
 * used by the stream.
 *
 * {{{
 * implicit object MyRequestBuilderForT extends RequestBuilder[CT, T] {
 *  override def request(ct: CT, t: T): ExecutableStatement =
 * ct.insert().value(_.name, t.name)
 * }
 * }}}
 *
 * @tparam CT the concrete [[CassandraTable]] implementation type
 * @tparam T the type of streamed elements
 */
trait RequestBuilder[CT <: CassandraTable[CT, T], T] {
  def request(ct: CT, t: T): ExecutableStatement
}
