package com.wonderland.elevator_sytem.application

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.StatusCodes
import akka.pattern.ask
import akka.http.scaladsl.server.{ExceptionHandler, MalformedRequestContentRejection, Route}
import akka.http.scaladsl.server.Directives.{complete, get, path, _}
import akka.util.Timeout
import com.wonderland.elevator_sytem.domain.Passenger
import com.wonderland.elevator_sytem.infrastructure.ElevatorSystem
import com.wonderland.elevator_sytem.infrastructure.ElevatorSystem._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class ElevatorSystemController(system: ActorSystem) extends Protocol {

  val elevatorSystem: ActorRef = system.actorOf(ElevatorSystem.props)
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val timeout: Timeout = 10.seconds

  val routes: Route =
    pathPrefix("elevatorSystem") {
      get {
        getStatus
      } ~
        post {
          receivePickUpPetition ~ receiveUpdatePetition
        }
    }

  def getStatus: Route = path("status") {
    val fResult = elevatorSystem ? GetSystemStatus
    onSuccess(fResult) {
      case s: SystemStatus => complete(s.toResponse)
    }
  }

  def receivePickUpPetition: Route = path("pickup") {
    entity(as[PickupPayload]) { pickup: PickupPayload =>
      val fResult = elevatorSystem ? PickUpPetition(Passenger(pickup.currentFloor, pickup.targetFloor))
      onSuccess(fResult) {
        case PetitionProcessed => complete(StatusCodes.Accepted)
        case NoAllowedPetition => complete(StatusCodes.BadRequest, "Some floor indicated is out of range")
      }
    }
  }

  def receiveUpdatePetition: Route = path("update") {
    entity(as[UpdatePayload]) { update: UpdatePayload =>
      val fResult = elevatorSystem ? (DispatchElevator(update.elevatorId, update.targetFloor))
      onSuccess(fResult) {
        case PetitionProcessed => complete(StatusCodes.Accepted)
        case NoAllowedPetition => complete(StatusCodes.BadRequest, "The floor indicated is out of range")
        case ElevatorDoesNotExist => complete(StatusCodes.NotFound, "There is not elevator with $elevatorId")

      }
    }
  }
}
