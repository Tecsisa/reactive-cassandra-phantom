package com.tecsisa.streams.cassandra

import akka.actor.ActorSystem
import com.datastax.driver.core.{ VersionNumber, Session }
import com.websudos.phantom.CassandraTable
import com.websudos.phantom.builder.query.ExecutableStatement
import com.websudos.phantom.connectors.{ KeySpace, ContactPoints, KeySpaceDef }
import com.websudos.phantom.dsl._
import com.websudos.phantom.keys.PartitionKey
import org.scalatest.{ Suite, BeforeAndAfterAll }

import scala.concurrent.{ Future, Await }
import scala.concurrent.duration._

trait CassandraTest extends BeforeAndAfterAll {
  _: Suite =>

  implicit val system = ActorSystem()
  import OperaTable.keySpace
  import OperaTable.session

  object OperaTable extends OperaTable with SimpleCassandraConnector {
    implicit val keySpace: KeySpace = KeySpace("streams")
  }

  implicit object OperaRequestBuilder extends RequestBuilder[OperaTable, Opera] {
    override def request(ct: OperaTable, t: Opera): ExecutableStatement =
      ct.insert().value(_.name, t.name)
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

case class Opera(name: String)

abstract class OperaTable extends CassandraTable[OperaTable, Opera] with SimpleCassandraConnector {
  object name extends StringColumn(this) with PartitionKey[String]
  def fromRow(row: Row): Opera = {
    Opera(name(row))
  }
  def count(): Future[Option[Long]] = {
    select.count().one()
  }
}

object OperaData {

  // Yes, you know... I like Wagner a lot :-)
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
  )

}

private object Defaults {
  def getDefaultConnector(host: String, port: Int, keySpace: String): KeySpaceDef = {
    ContactPoints(Seq(host), port).keySpace(keySpace)
  }
}

trait SimpleCassandraConnector {

  private[this] lazy val connector = Defaults.getDefaultConnector(host, port, keySpace.name)

  def host: String = "localhost"

  def port: Int = 9142

  implicit def keySpace: KeySpace

  implicit lazy val session: Session = connector.session

  def cassandraVersion: VersionNumber = connector.cassandraVersion

  def cassandraVersions: Set[VersionNumber] = connector.cassandraVersions
}
