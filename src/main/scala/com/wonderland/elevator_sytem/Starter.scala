package com.wonderland.elevator_sytem

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.wonderland.elevator_sytem.application.ElevatorSystemController
import com.wonderland.elevator_sytem.infrastructure.SystemSettings

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Starter extends App {

  implicit val actorSystem: ActorSystem = ActorSystem("Elevator-System")

  implicit val materializer: ActorMaterializer = ActorMaterializer()(actorSystem)

  val settings: SystemSettings = SystemSettings(actorSystem)

  val systemController = new ElevatorSystemController(actorSystem)

  Await.result(Http().bindAndHandle(systemController.routes, "0.0.0.0", 9000), Duration.Inf)

}
