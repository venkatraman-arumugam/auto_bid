ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

//resolvers += "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/"


lazy val root = (project in file("."))
  .settings(
    name := "auto-bid"
  )

mainClass in (Compile, run) := Some("Main")

val AkkaVersion = "2.6.19"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-persistence-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,

"ch.qos.logback" % "logback-classic" % "1.1.3" % Runtime

  //  "com.typesafe.akka" %% "akka-persistence-testkit" % AkkaVersion % Test,
//  "com.typesafe.akka" %% "akka-cluster-typed" % AkkaVersion,
//  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
//  "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
//  "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
//  "ch.qos.logback" % "logback-classic" % "1.2.11"

)
libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.4" % Test
libraryDependencies += "com.typesafe.play" %% "play-json" % "2.9.2"

//libraryDependencies += "com.typesafe.play" %% "play-json" % "2.16.8"
