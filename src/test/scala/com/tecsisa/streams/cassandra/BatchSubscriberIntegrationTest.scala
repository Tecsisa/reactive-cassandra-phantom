package com.tecsisa.streams.cassandra

import java.util.concurrent.{ CountDownLatch, TimeUnit }

import akka.testkit.{ TestProbe, TestActorRef }
import com.websudos.phantom.batch.BatchType
import org.reactivestreams.{ Subscriber, Publisher, Subscription }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{ FlatSpec, Matchers }

class BatchSubscriberIntegrationTest extends FlatSpec with MockitoSugar with Matchers with ScalaFutures with CassandraTest {

  import OperaTable.{ keySpace, session }
  import ReactiveCassandra._

  it should "persist all data" in {
    val completionLatch = new CountDownLatch(1)
    val subscriber = OperaTable.subscriber(2, 2, completionFn = () => completionLatch.countDown())
    OperaPublisher.subscribe(subscriber)
    completionLatch.await(5, TimeUnit.SECONDS)

    OperaTable.count().futureValue.get shouldBe OperaData.operas.length
  }

  it should "persist all data even if the subscriber never completes" in {
    import scala.concurrent.duration._

    val expected = OperaData.operas.length
    // The short interval is just for the sake of test execution time, it's not a recommendation
    val subscriber = OperaTable.subscriber(2, 1, flushInterval = Some(500.millis))
    OperaEndlessPublisher.subscribe(subscriber)

    blockUntil(s"Expected count of $expected") { () => OperaTable.count().futureValue.get == expected }

  }

  it should "actor is terminated when send an exception message" in {
    val subscriberMock = mock[OperaTable]
    val subscriber = subscriberMock.subscriber(5, 2)

    var remaining = OperaData.operas
    val s = new Subscription {
      override def cancel(): Unit = ()
      override def request(l: Long): Unit = {
        remaining.take(l.toInt).foreach(subscriber.onNext)
        remaining = remaining.drop(l.toInt)
        if (remaining.isEmpty)
          subscriber.onComplete()
      }
    }

    val actorRef = TestActorRef(new BatchActor[OperaTable, Opera](
      OperaTable,
      OperaRequestBuilder,
      s,
      5,
      2,
      batchType = BatchType.Unlogged,
      flushInterval = None,
      completionFn = () => (),
      errorFn = _ => (),
      maxRetries = 1
    ))

    // Watching Actor from Probes
    val probe = TestProbe()
    probe.watch(actorRef)

    // Send an exception
    probe.send(actorRef, new Exception())

    // Check that actor is terminated
    probe.expectTerminated(actorRef)

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

object OperaEndlessPublisher extends Publisher[Opera] {

  override def subscribe(s: Subscriber[_ >: Opera]): Unit = {
    var remaining = OperaData.operas
    s.onSubscribe(new Subscription {
      override def cancel(): Unit = ()
      override def request(l: Long): Unit = {
        remaining.take(l.toInt).foreach(s.onNext)
        remaining = remaining.drop(l.toInt)
      }
    })
  }

}

