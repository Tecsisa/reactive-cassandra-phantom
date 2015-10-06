import sbt._

object Deps {

  object Reactive {
    val ReactiveVersion = "1.0.0"
    val streams = apply("streams")
    val streamsTck = apply("streams-tck") % "test"

    private def apply(moduleName: String) = "org.reactivestreams" % s"reactive-$moduleName" % ReactiveVersion
  }

  object Akka {
    val AkkaVersion = "2.4.0"
    val AkkaStreamVersion = "1.0"
    val actor = apply("actor")
    val slf4j = apply("slf4j")

    private def apply(moduleName: String) = "com.typesafe.akka" %% s"akka-$moduleName" % AkkaVersion
  }

  object Phantom {
    val dsl = apply("dsl")
    val testkit = apply("testkit") % "test"

    private def apply(moduleName: String) = "com.websudos" %% s"phantom-$moduleName" % "1.12.2"
  }

  object Testing {
    val scalaTest = "org.scalatest" %% "scalatest" % "2.2.4" % "test"
  }

  object LoggingFrameworks {
    val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0"
    val logBack = "ch.qos.logback" % "logback-classic" % "1.1.3"
  }

}
