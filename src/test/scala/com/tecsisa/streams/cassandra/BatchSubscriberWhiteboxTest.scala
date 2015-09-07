package com.tecsisa.streams.cassandra

import akka.actor.ActorSystem
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.builder.query.ExecutableStatement
import com.websudos.phantom.connectors.KeySpace
import com.websudos.phantom.dsl.{Row, StringColumn}
import com.websudos.phantom.keys.PartitionKey
import com.websudos.phantom.testkit.suites.SimpleCassandraConnector
import org.reactivestreams.{Subscription, Subscriber}
import org.reactivestreams.tck.SubscriberWhiteboxVerification.{SubscriberPuppet, WhiteboxSubscriberProbe}
import org.reactivestreams.tck.{TestEnvironment, SubscriberWhiteboxVerification}
import org.scalatest.{Ignore, BeforeAndAfterAll}
import org.scalatest.testng.TestNGSuiteLike

import scala.concurrent.duration._

import scala.concurrent.Await

//@Ignore
//class BatchSubscriberWhiteboxTest
//  extends SubscriberWhiteboxVerification[Opera](new TestEnvironment(DEFAULT_TIMEOUT_MILLIS))
//  with TestNGSuiteLike with BeforeAndAfterAll {
//
//  implicit val system = ActorSystem()
//  import OperaTable.session
//  import OperaTable.keySpace
//
//  object OperaTable extends OperaTable with SimpleCassandraConnector {
//    implicit val keySpace: KeySpace = KeySpace("streams")
//  }
//
//  object OperaRequestBuilder extends RequestBuilder[OperaTable, Opera] {
//    override def request(ct: OperaTable, t: Opera): ExecutableStatement =
//      ct.insert().value(_.name, t.name)
//  }
//
//  override def createSubscriber(probe: WhiteboxSubscriberProbe[Opera]): Subscriber[Opera] = {
//    new BatchSubscriber[OperaTable, Opera](
//      OperaTable,
//      OperaRequestBuilder,
//      5,
//      2) {
//
//      override def onSubscribe(s: Subscription): Unit = {
//        super.onSubscribe(s)
//
//        probe.registerOnSubscribe(new SubscriberPuppet {
//
//          override def triggerRequest(elements: Long): Unit = {
//            s.request(elements)
//          }
//
//          override def signalCancel(): Unit = {
//            s.cancel()
//          }
//        })
//      }
//
//      override def onComplete(): Unit = {
//        super.onComplete()
//        probe.registerOnComplete()
//      }
//
//      override def onError(t: Throwable): Unit = {
//        probe.registerOnError(t)
//      }
//
//      override def onNext(t: Opera): Unit = {
//        super.onNext(t)
//        probe.registerOnNext(t)
//      }
//
//    }
//  }
//
//  override def createElement(element: Int): Opera = operas(element)
//
//  val operas = Array(
//    Opera("Leubald"),
//    Opera("Die Laune des Verliebten"),
//    Opera("Die Hochzeit"),
//    Opera("Die Feen"),
//    Opera("Das Liebesverbot"),
//    Opera("Die hohe Braut"),
//    Opera("Männerlist größer als Frauenlist, oder Die glückliche Bärenfamilie"),
//    Opera("Rienzi, der letzte der Tribunen"),
//    Opera("Der fliegende Holländer"),
//    Opera("Die Sarazenin"),
//    Opera("Die Bergwerke zu Falun"),
//    Opera("Tannhäuser und der Sängerkrieg auf dem Wartburg"),
//    Opera("Lohengrin"),
//    Opera("Friedrich I"),
//    Opera("Jesus von Nazareth"),
//    Opera("Achilleus"),
//    Opera("Wieland der Schmied"),
//    Opera("Das Rheingold"),
//    Opera("Die Walküre"),
//    Opera("Die Sieger"),
//    Opera("Tristan und Isolde"),
//    Opera("Die Meistersinger von Nürnberg"),
//    Opera("Luthers Hochzeit"),
//    Opera("Siegfried"),
//    Opera("Götterdämmerung"),
//    Opera("Eine Kapitulation"),
//    Opera("Parsifal")
//  )
//
//  override def beforeAll() {
//    Await.result(OperaTable.create.ifNotExists().future(), 5.seconds)
//  }
//
//  override def afterAll(): Unit = {
//    system.terminate()
//    if (!session.isClosed)
//      session.close()
//  }
//
//}



