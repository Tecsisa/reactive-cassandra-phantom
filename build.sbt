import Deps._

name := "reactive-cassandra-phantom"

organization := "com.tecsisa.wr"

version := "0.0.1-SNAPSHOT"

scalaVersion := "2.11.7"

crossScalaVersions := Seq("2.11.7", "2.10.5")

scalacOptions := Seq("-deprecation", "-target:jvm-1.8", "-encoding", "utf-8")

javacOptions := Seq("-g:none")

resolvers += Resolver.bintrayRepo("websudos", "oss-releases")

libraryDependencies ++= {
  Seq(
    Akka.actor,
    Reactive.streams,
    Phantom.dsl,
    LoggingFrameworks.logBack,
    Testing.scalaTest,
    Reactive.streamsTck
  )
}

publishMavenStyle := true

PhantomSbtPlugin.projectSettings

scalariformSettings
