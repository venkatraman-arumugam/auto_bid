ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

lazy val root = (project in file("."))
  .settings(
    name := "auto-bid"
  )

mainClass in (Compile, run) := Some("Main")

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.6.19"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.12" % Test