resolvers ++= Seq(
  Resolver.bintrayRepo("websudos", "oss-releases")
)

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")

addSbtPlugin("com.sphonic" %% "phantom-sbt" % "0.3.0")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.3.0")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.0.2")