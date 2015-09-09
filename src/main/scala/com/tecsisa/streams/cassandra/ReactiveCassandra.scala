package com.tecsisa.streams.cassandra

import akka.actor.ActorSystem
import com.websudos.phantom.batch.BatchType
import com.websudos.phantom.dsl._

import scala.concurrent.duration.FiniteDuration

/**
 * Just a wrapper module for enhancing phantom [[CassandraTable]]
 * with reactive streams features.
 *
 * In order to be used, please be sured to import the implicits
 * into the scope.
 *
 * {{{
 * import ReactiveCassandra._
 * val subscriber = CassandraTableInstance.subscriber()
 * }}}
 *
 * @see [[http://www.reactive-streams.org/]]
 * @see [[https://github.com/websudos/phantom]]
 */
object ReactiveCassandra {

  implicit class StreamedCassandraTable[T](ct: CassandraTable[_, T]) {

    /**
     * Gets a reactive streams [[org.reactivestreams.Subscriber]] with
     * batching capabilities for some phantom [[CassandraTable]]. This
     * subscriber is able to work for both finite short-lived streams
     * and never-ending long-lived streams. For the latter, a flushInterval
     * parameter can be used.
     *
     * @param batchSize the number of elements to include in the Cassandra batch
     * @param concurrentRequests the number of concurrent batch operations
     * @param batchType the type of the batch.
     *                  @see See [[http://docs.datastax.com/en/cql/3.1/cql/cql_reference/batch_r.html]] for further
     *                       explanation.
     * @param flushInterval used to schedule periodic batch execution even though the number of statements hasn't
     *                      been reached yet. Useful in never-ending streams that will never been completed.
     * @param completionFn a function that will be invoked when the stream is completed
     * @param errorFn a function that will be invoked when an error occurs
     * @param builder an implicitly resolved [[RequestBuilder]] that wraps a phantom [[com.websudos.phantom.builder.query.ExecutableStatement]].
     *                Every T element that gets into the stream from the upstream is turned into a ExecutableStatement
     *                by means of this builder.
     * @param system the underlying [[ActorSystem]]. This [[org.reactivestreams.Subscriber]] implementation uses Akka
     *               actors, but is not restricted to be used in the context of Akka Streams.
     * @param session the Cassandra [[com.datastax.driver.core.Session]]
     * @param space the Cassandra [[KeySpace]]
     * @param ev an evidence to get the T type removed by erasure
     * @tparam CT the concrete type inheriting from [[CassandraTable]]
     * @tparam T the type of the streamed element
     * @return the [[org.reactivestreams.Subscriber]] to be connected to a reactive stream typically initiated by
     *         a [[org.reactivestreams.Publisher]]
     */
    def subscriber[CT <: CassandraTable[CT, T]](
      batchSize: Int = 100,
      concurrentRequests: Int = 5,
      batchType: BatchType = BatchType.Unlogged,
      flushInterval: Option[FiniteDuration] = None,
      completionFn: () => Unit = () => (),
      errorFn: Throwable => Unit = _ => ())(implicit builder: RequestBuilder[CT, T],
        system: ActorSystem, session: Session, space: KeySpace, ev: Manifest[T]): BatchSubscriber[CT, T] = {
      new BatchSubscriber[CT, T](
        ct.asInstanceOf[CT], // not being able to get rid of this casting. Can anyone help?.
        builder,
        batchSize,
        concurrentRequests,
        batchType,
        flushInterval,
        completionFn,
        errorFn
      )
    }
  }

}
