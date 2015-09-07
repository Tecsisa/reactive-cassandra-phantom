package com.tecsisa.streams.cassandra

import com.websudos.phantom.CassandraTable
import com.websudos.phantom.column.PrimitiveColumn
import com.websudos.phantom.dsl._
import com.websudos.phantom.keys.PartitionKey
import com.websudos.phantom.testkit.suites.SimpleCassandraConnector

case class Opera(name: String)

abstract class OperaTable extends CassandraTable[OperaTable, Opera] with SimpleCassandraConnector {
  object name extends StringColumn(this) with PartitionKey[String]
  def fromRow(row: Row): Opera = {
    Opera(name(row))
  }
}

object OperaData {

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
