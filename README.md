*This project has been discontinued as this feature has been roughly [included](https://github.com/websudos/phantom/pull/348) in the phantom project itself.*

Reactive Cassandra (with Phantom DSL)
=================================

[![Build Status](https://travis-ci.org/Tecsisa/reactive-cassandra-phantom.svg?branch=master)](https://travis-ci.org/Tecsisa/reactive-cassandra-phantom)
[![Coverage Status](https://coveralls.io/repos/Tecsisa/reactive-cassandra-phantom/badge.svg?branch=master&service=github)](https://coveralls.io/github/Tecsisa/reactive-cassandra-phantom?branch=master)

A [reactive streams] compatible subscriber for Cassandra. This is a [phantom] based effort and it's strongly influenced by [elastic4s].

The idea behind this library is to provide a reactive streams subscriber (and maybe a publisher later) for working with Cassandra database and, at the same time, taking advantage of phantom dsl to build CQL3 statements idiomatically with Scala. Every element that downstreams to this subscriber can be turn into a new statement that will be added to a batch query.

This library leverages the [typeclass pattern] in order to keep separate both the streaming logic and the query statements itself that remain under the responsibility of programmers. An implicit class enables Phantom CassandraTable to be enhanced with streaming capabilities so that integration with phantom legacy code is simple and straightforward.

The library is published on [Tecsisa's bintray] and it's currently on version 0.0.8.

## Usage

In order to use this library, just add the Tecsisa bintray repository to the resolvers list in your build.sbt file:

```Scala
  resolvers += Resolver.url("bintray-tecsisa-repo",
                                     url("http://dl.bintray.com/tecsisa/maven-bintray-repo"))
```

Or if you're using the [bintray-sbt] plugin:

```Scala
  Resolver.bintrayRepo("tecsisa", "maven-bintray-repo")
```

And then, just import the dependency:

```Scala
  libraryDependencies += "com.tecsisa" %% "reactive-cassandra-phantom" % "0.0.8"
```

### Examples of use

Let's suppose that we're requesting a list of Wagner's operas via a reactive stream. In this case, the element streamed could be modeled as:

```Scala
  case class Opera(name: String)
```

Following phantom's dsl we could model a Cassandra table to persist all this data as:

```Scala
  abstract class OperaTable extends CassandraTable[OperaTable, Opera] with SimpleCassandraConnector {
  object name extends StringColumn(this) with PartitionKey[String]
  def fromRow(row: Row): Opera = {
    Opera(name(row))
  }
  def count(): Future[Option[Long]] = {
    select.count().one()
  }
}
```

Please, refer to phantom's documentation for further explanation. Of course, you'll also need a Cassandra connector in place:

```Scala
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
```

And some data:

```Scala
  object OperaData {

  // Yes, you know... I like Wagner a lot :-)
  val operas = Array(
    Opera("Das Rheingold"),
    Opera("Die Walküre"),
    Opera("Die Sieger"),
    Opera("Tristan und Isolde"),
    Opera("Die Meistersinger von Nürnberg"),
    Opera("Luthers Hochzeit"),
    Opera("Siegfried"),
    Opera("Götterdämmerung"),
    Opera("Eine Kapitulation"),
    Opera("Parsifal"),
    ........
  )
}
```

So far, all this is phantom stuff. Now, you'll need to bring some implicits to scope:

```Scala
  com.tecsisa.streams.cassandra.ReactiveCassandra._
```

This implicit will put your cassandra table on steroids and you'll be able to build your subscriber easily:

```Scala
  val subscriber = OperaTable.subscriber(50, 4, completionFn = () => println("streaming finished!"))
```

In order to get this up and running, you'll need to bring some implicit context in scope:

- an Akka actor system reference
- the Cassandra session and keyspace
- and one implementation of the RequestBuilder typeclass that contains the statement to be invoked once for streamed element.

```Scala
  implicit val system = ActorSystem()
  import OperaTable.{ keySpace, session }

  object OperaTable extends OperaTable with SimpleCassandraConnector {
    implicit val keySpace: KeySpace = KeySpace("streams")
  }

  implicit object OperaRequestBuilder extends RequestBuilder[OperaTable, Opera] {
    override def request(ct: OperaTable, t: Opera): ExecutableStatement =
      ct.insert().value(_.name, t.name)
  }  
```

In this case, we'll make an insert for every element streamed.

This subscriber can take part of standard reactive streams pipelines being connected to some publisher:

```Scala
  OperaPublisher.subscribe(subscriber)

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
```

Of course, it's also possible to use this subscriber with Akka streams pipelines following the integration guidelines provided in the [Akka Streams documentation].

Currently, three kinds of CQL3 batches can be carried out:

- Logged
- Unlogged
- Counter

Please, refer to the [CQL3 documentation] for further explanation.

## Next Steps

- A proper publisher to face the other side of the problem.
- Incorporate prepared statements as soon as they're available in phantom. This addition should improve the streaming performance greatly.

## Related Work

- [elastic4s - Elasticsearch Scala Client]
- [Cassandra + Phantom Example]

[reactive streams]: http://www.reactive-streams.org/
[phantom]: https://github.com/websudos/phantom
[elastic4s]: https://github.com/sksamuel/elastic4s
[typeclass pattern]: http://danielwestheide.com/blog/2013/02/06/the-neophytes-guide-to-scala-part-12-type-classes.html
[Tecsisa's bintray]: https://bintray.com/tecsisa/maven-bintray-repo
[bintray-sbt]: https://github.com/softprops/bintray-sbt
[Akka Streams documentation]:  http://doc.akka.io/docs/akka-stream-and-http-experimental/1.0/scala/stream-integrations.html#Integrating_with_Reactive_Streams
[CQL3 documentation]: http://docs.datastax.com/en/cql/3.1/cql/cql_reference/batch_r.html
[elastic4s - Elasticsearch Scala Client]: https://github.com/sksamuel/elastic4s
[Cassandra + Phantom Example]: https://github.com/thiagoandrade6/cassandra-phantom
