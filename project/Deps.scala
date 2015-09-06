import sbt._

object Deps {

  object Reactive {
    val Streams = "org.reactivestreams" % "reactive-streams" % "1.0.0"
  }

  object Akka {
    val AkkaVersion = "2.4.0-RC2"
    val AkkaStreamVersion = "1.0"
    val actor = apply("actor")

    private def apply(moduleName: String) = "com.typesafe.akka" %% s"akka-$moduleName" % AkkaVersion

    object Experimental {
      val stream = apply("stream")

      private def apply(moduleName: String) =
        "com.typesafe.akka" %% s"akka-$moduleName-experimental" % AkkaStreamVersion
    }
  }

  object Phantom {
    val dsl = apply("dsl")
    val testkit = apply("testkit")

    private def apply(moduleName: String) = "com.websudos" %% s"phantom-$moduleName" % "1.12.2"
  }

  object Testing {
    val scalaTest = "org.scalatest" %% "scalatest" % "2.2.4" % "test"
  }

}
