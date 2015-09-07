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
    Source(() => OperaData.operas.toIterator).to(Sink(subscriber)).run()
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

}