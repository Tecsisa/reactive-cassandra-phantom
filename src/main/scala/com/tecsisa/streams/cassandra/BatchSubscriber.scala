package com.tecsisa.streams.cassandra

import akka.actor.{Props, Actor, ActorRef, ActorSystem}
import com.datastax.driver.core.ResultSet
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.batch.{BatchType, BatchQuery}
import com.websudos.phantom.builder.query.{UsingPart, ExecutableStatement}
import com.websudos.phantom.dsl._
import org.reactivestreams.{Subscription, Subscriber}

import scala.collection.mutable.ArrayBuffer
import scala.util.{Success, Failure}

class BatchSubscriber[CT <: CassandraTable[CT, T], T] private[cassandra](
    table: CT,
    builder: RequestBuilder[CT, T],
    batchSize: Int,
    concurrentRequests: Int,
    completionFn: () => Unit)
    (implicit system: ActorSystem, session: Session, space: KeySpace, ev: Manifest[T]) extends Subscriber[T] {

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
            completionFn)
        )
      )
      s.request(batchSize * concurrentRequests)
    } else {
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
}

class BatchActor[CT <: CassandraTable[CT, T], T](
    table: CT,
    builder: RequestBuilder[CT, T],
    subscription: Subscription,
    batchSize: Int,
    concurrentRequests: Int,
    completionFn: () => Unit)
    (implicit session: Session, space: KeySpace, ev: Manifest[T]) extends Actor {

  import context.dispatcher

  private val buffer = new ArrayBuffer[T]()
  buffer.sizeHint(batchSize)

  private var completed = false

  def receive = {
    case t: Throwable =>
      handleError(t)

    case BatchActor.Completed =>
      if (buffer.nonEmpty)
        executeStatements()
      completed = true

    case rs: ResultSet =>
      if (completed) shutdown()
      else subscription.request(batchSize)

    case t: T =>
      buffer.append(t)
      if (buffer.size == batchSize)
        executeStatements()
  }

  private def shutdown(): Unit = {
    completionFn()
    context.stop(self)
  }

  private def handleError(t: Throwable): Unit = {
    subscription.cancel()
    buffer.clear()
    context.stop(self)
  }

  private def executeStatements(): Unit = {
    val query = new BatchQuery(
      buffer.map(builder.request(table, _).qb).toIterator,
      BatchType.Unlogged,
      UsingPart.empty,
      false,
      None)
    query.future().onComplete {
      case Failure(e) => self ! e
      case Success(resp) => self ! resp
    }
    buffer.clear()
  }

}

trait RequestBuilder[CT <: CassandraTable[CT, T], T] {
    def request(ct: CT, t: T): ExecutableStatement
}