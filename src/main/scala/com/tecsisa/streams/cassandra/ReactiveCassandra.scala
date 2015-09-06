package com.tecsisa.streams.cassandra

import akka.actor.ActorSystem
import com.websudos.phantom.dsl._

object ReactiveCassandra {

  implicit class EnhancedCassandraTable[T](ct: CassandraTable[_, T]) {

    def subscriber[CT <: CassandraTable[CT, T]](batchSize: Int = 100, concurrentRequests: Int = 5)
                  (implicit builder: RequestBuilder[CT, T],
                   system: ActorSystem, session: Session, space: KeySpace, ev: Manifest[T]): BatchSubscriber[_, T] = {
      new BatchSubscriber[CT, T](
        ct.asInstanceOf[CT],
        builder,
        batchSize,
        concurrentRequests
      )
    }
  }

}
