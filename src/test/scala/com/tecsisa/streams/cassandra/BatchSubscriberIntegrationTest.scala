package com.tecsisa.streams.cassandra

import java.util.concurrent.{CountDownLatch, TimeUnit}

import org.reactivestreams.{Publisher, Subscriber, Subscription}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}

class BatchSubscriberIntegrationTest extends FlatSpec with Matchers with ScalaFutures with CassandraTest {

  import OperaTable.{keySpace, session}
  import ReactiveCassandra._

  it should "persist all data" in {
    val completionLatch = new CountDownLatch(1)
    val subscriber = OperaTable.subscriber(2, 2, completionFn = () => completionLatch.countDown())
    OperaPublisher.subscribe(subscriber)
    completionLatch.await(5, TimeUnit.SECONDS)

    OperaTable.count().futureValue.get shouldBe OperaData.operas.length

  }

}

object OperaPublisher extends Publisher[Opera] {

  override def subscribe(s: Subscriber[_ >: Opera]): Unit = {
    var remaining = OperaData.operas
    s.onSubscribe(new Subscription {
      override def cancel(): Unit = ()
      override def request(l: Long): Unit = {
        remaining.take(l.toInt).foreach(s.onNext)
        remaining = remaining.drop(l.toInt)
        if (remaining.isEmpty)
          s.onComplete()
      }
    })
  }

}
