package com.tecsisa.streams.cassandra

import akka.actor.{ Props, Actor, ActorRef, ActorSystem }
import com.datastax.driver.core.ResultSet
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.batch.{ BatchType, BatchQuery }
import com.websudos.phantom.builder.query.{ UsingPart, ExecutableStatement }
import com.websudos.phantom.dsl._
import org.reactivestreams.{ Subscription, Subscriber }

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.FiniteDuration
import scala.util.{ Success, Failure }

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
    errorFn: Throwable => Unit)(implicit system: ActorSystem, session: Session, space: KeySpace, ev: Manifest[T]) extends Subscriber[T] {

  private var actor: ActorRef = _

  override def onSubscribe(s: Subscription): Unit = {
    if (s == null) throw new NullPointerException()
    if (actor == null) {
      actor = system.actorOf(
        Props(
          new BatchActor(
            table,
            builder,
            s,
            batchSize,
            concurrentRequests,
            batchType,
            flushInterval,
            completionFn,
            errorFn
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
  case object ForceExecution
}

class BatchActor[CT <: CassandraTable[CT, T], T](
    table: CT,
    builder: RequestBuilder[CT, T],
    subscription: Subscription,
    batchSize: Int,
    concurrentRequests: Int,
    batchType: BatchType,
    flushInterval: Option[FiniteDuration],
    completionFn: () => Unit,
    errorFn: Throwable => Unit)(implicit session: Session, space: KeySpace, ev: Manifest[T]) extends Actor {

  import context.{ dispatcher, system }

  private val buffer = new ArrayBuffer[T]()
  buffer.sizeHint(batchSize)

  private var completed = false

  /** It's only created if a flushInterval is provided */
  private val scheduler = flushInterval.map { interval =>
    system.scheduler.schedule(interval, interval, self, BatchActor.ForceExecution)
  }

  def receive = {
    case t: Throwable =>
      handleError(t)

    case BatchActor.Completed =>
      if (buffer.nonEmpty)
        executeStatements()
      completed = true

    case BatchActor.ForceExecution =>
      if (buffer.nonEmpty)
        executeStatements()

    case rs: ResultSet =>
      if (completed) shutdown()
      else subscription.request(batchSize)

    case t: T =>
      buffer.append(t)
      if (buffer.size == batchSize)
        executeStatements()
  }

  // Stops the scheduler if it exists
  override def postStop() = scheduler.map(_.cancel())

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

  private def executeStatements(): Unit = {
    val query = new BatchQuery(
      buffer.map(builder.request(table, _).qb).toIterator,
      batchType,
      UsingPart.empty,
      false,
      None
    )
    query.future().onComplete {
      case Failure(e) => self ! e
      case Success(resp) => self ! resp
    }
    buffer.clear()
  }

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