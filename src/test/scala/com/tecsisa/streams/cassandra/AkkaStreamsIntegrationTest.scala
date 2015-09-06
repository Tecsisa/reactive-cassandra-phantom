package com.tecsisa.streams.cassandra

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import com.websudos.phantom.builder.query.ExecutableStatement
import com.websudos.phantom.connectors.KeySpace
import com.websudos.phantom.testkit.suites.SimpleCassandraConnector
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, FlatSpec}

import scala.concurrent.Await
import scala.concurrent.duration._

import com.tecsisa.streams.cassandra.ReactiveCassandra._

class AkkaStreamsIntegrationTest extends FlatSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import OperaTable.session
  import OperaTable.keySpace

  object OperaTable extends OperaTable with SimpleCassandraConnector {
    implicit val keySpace: KeySpace = KeySpace("streams")
  }

  implicit val builder = new RequestBuilder[OperaTable, Opera] {
    override def request(ct: OperaTable, t: Opera): ExecutableStatement =
      ct.insert().value(_.name, t.name)
  }

  it should "works" in {
    val subscriber = OperaTable.subscriber(5, 1)
    Source(() => operas).to(Sink(subscriber)).run()
    Thread.sleep(5000)
  }

  override def beforeAll() {
    Await.result(OperaTable.create.ifNotExists().future(), 5.seconds)
  }

  override def afterAll(): Unit = {
    system.terminate()
    if (!session.isClosed)
      session.close()
  }

  val operas = Array(
    Opera("Leubald"),
    Opera("Die Laune des Verliebten"),
    Opera("Die Hochzeit"),
    Opera("Die Feen"),
    Opera("Das Liebesverbot"),
    Opera("Die hohe Braut"),
    Opera("Männerlist größer als Frauenlist, oder Die glückliche Bärenfamilie"),
    Opera("Rienzi, der letzte der Tribunen"),
    Opera("Der fliegende Holländer"),
    Opera("Die Sarazenin"),
    Opera("Die Bergwerke zu Falun"),
    Opera("Tannhäuser und der Sängerkrieg auf dem Wartburg"),
    Opera("Lohengrin"),
    Opera("Friedrich I"),
    Opera("Jesus von Nazareth"),
    Opera("Achilleus"),
    Opera("Wieland der Schmied"),
    Opera("Das Rheingold"),
    Opera("Die Walküre"),
    Opera("Die Sieger"),
    Opera("Tristan und Isolde"),
    Opera("Die Meistersinger von Nürnberg"),
    Opera("Luthers Hochzeit"),
    Opera("Siegfried"),
    Opera("Götterdämmerung"),
    Opera("Eine Kapitulation"),
    Opera("Parsifal")
  ).toIterator

}