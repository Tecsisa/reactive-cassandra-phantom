package com.tecsisa.streams.cassandra

import org.reactivestreams.tck.SubscriberWhiteboxVerification.{SubscriberPuppet, WhiteboxSubscriberProbe}
import org.reactivestreams.tck.{SubscriberWhiteboxVerification, TestEnvironment}
import org.reactivestreams.{Subscriber, Subscription}
import org.scalatest.testng.TestNGSuiteLike

class BatchSubscriberWhiteboxTest
  extends SubscriberWhiteboxVerification[Opera](new TestEnvironment(DEFAULT_TIMEOUT_MILLIS))
  with TestNGSuiteLike with CassandraTest {

  import OperaTable.{keySpace, session}

  override def createSubscriber(probe: WhiteboxSubscriberProbe[Opera]): Subscriber[Opera] = {
    new BatchSubscriber[OperaTable, Opera](
      OperaTable,
      OperaRequestBuilder,
      5,
      2,
      completionFn = () => ()) {

      override def onSubscribe(s: Subscription): Unit = {
        super.onSubscribe(s)

        probe.registerOnSubscribe(new SubscriberPuppet {

          override def triggerRequest(elements: Long): Unit = {
            s.request(elements)
          }

          override def signalCancel(): Unit = {
            s.cancel()
          }
        })
      }

      override def onComplete(): Unit = {
        super.onComplete()
        probe.registerOnComplete()
      }

      override def onError(t: Throwable): Unit = {
        probe.registerOnError(t)
      }

      override def onNext(t: Opera): Unit = {
        super.onNext(t)
        probe.registerOnNext(t)
      }

    }
  }

  override def createElement(element: Int): Opera = OperaData.operas(element)

}



