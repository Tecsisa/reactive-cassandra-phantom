package com.tecsisa.streams.cassandra

import akka.actor.ActorSystem
import com.websudos.phantom.batch.BatchType
import com.websudos.phantom.dsl._

object ReactiveCassandra {

  implicit class StreamedCassandraTable[T](ct: CassandraTable[_, T]) {

    def subscriber[CT <: CassandraTable[CT, T]](
         batchSize: Int = 100,
         concurrentRequests: Int = 5,
         batchType: BatchType = BatchType.Unlogged,
         completionFn: () => Unit = () => ())
        (implicit builder: RequestBuilder[CT, T],
         system: ActorSystem, session: Session, space: KeySpace, ev: Manifest[T]): BatchSubscriber[CT, T] = {
      new BatchSubscriber[CT, T](
        ct.asInstanceOf[CT], // not being able to get rid of this casting. Can anyone help?.
        builder,
        batchSize,
        concurrentRequests,
        batchType,
        completionFn)
    }
  }

}
