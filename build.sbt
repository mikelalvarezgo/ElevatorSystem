name := "wonderland_elevator_system"

version := "0.1"

scalaVersion := "2.12.7"

mainClass := Some("com.wonderland.elevator_system.Starter")

scalacOptions += "-Ypartial-unification"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http"   % "10.1.5",
  "com.typesafe.akka" %% "akka-http-testkit" % "10.1.5",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.1.5",
  "com.typesafe.akka" %% "akka-actor"  % "2.5.4",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.20" % "test",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test"
  )
