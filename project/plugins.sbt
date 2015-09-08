resolvers ++= Seq(
  Resolver.bintrayRepo("websudos", "oss-releases")
)

addSbtPlugin("me.lessis" % "bintray-sbt" % "0.3.0")

addSbtPlugin("com.sphonic" %% "phantom-sbt" % "0.3.0")
