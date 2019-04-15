package com.wonderland.elevator_sytem.infrastructure

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.wonderland.elevator_sytem.domain._
import com.wonderland.elevator_sytem.infrastructure.Elevator.{Dispatch, GetState, StateMessage}
import ElevatorSystem._

/*
 *This actor represent the system of elevator. It will manage the message coming from outside the system
 *and manage all the elevator of the system. It provide the functionality for the next interface:
 *
 *  Querying the state of the elevators (what floor are they on and where they are going)
 *
 *  Receiving an update about the status of an elevator
 *
 *  Receiving a pickup request
 */

class ElevatorSystem extends Actor with ActorLogging {

  implicit val executionContext = context.dispatcher

  val settings = SystemSettings(context.system)
  val numberOfElevators = if (settings.numberOfElevator > 16) {
    log.info("Number of elevators in setting exceeds 16, setting number of elevator to 16")
    16
  } else settings.numberOfElevator

  val floorInterval = (0 to settings.numberOfFloors).toSet


  val elevators: Map[Int, ActorRef] =
    (1 to numberOfElevators).map(id => id -> context.actorOf(Elevator.props(id))).toMap

  override def receive: Receive = {
    case GetSystemStatus =>
      log.debug(s"$header Getting system status ...")
      context.actorOf(ElevatorStatusRetriever.props(sender(), elevators.values.toList, None)) ! GetSystemStatus
    case PickUpPetition(passenger) =>
      def isValidPetition(passenger: Passenger): Boolean = {
        floorInterval.contains(passenger.currentFloor) && floorInterval.contains(passenger.targetFloor)
      }

      if (isValidPetition(passenger)) {
        log.info(s"Querying elevator system for attending pickup petition for passenger $passenger")
        context.actorOf(ElevatorStatusRetriever.props(
          self,
          elevators.values.toList,
          Some(passenger))) ! GetSystemStatus
        sender ! PetitionProcessed
      } else {
        log.warning(s"$header Pickup petition is not valid for ${passenger}," +
          s" some floor is out of range of floors: ${floorInterval}")
        sender ! NoAllowedPetition
      }

    case SystemStatus(states, Some(passenger)) =>
      val oElevatorChosen = bestElevatorToPick(states, passenger)
      oElevatorChosen match {
        case Some(elevatorId) => {
          log.info(s"$header Sending petition of passenger $passenger to elevator $elevatorId")
          elevators(elevatorId) ! PickUpPetition(passenger)
        }
        case _ => {
          log.warning(s"$header No elevator available for passenger $passenger, system will retry to get an elevator" +
            s"in ${settings}seconds")
          val captSelf = self
          context.system.scheduler.scheduleOnce(settings.pickUpRetryPeriod, captSelf, PickUpPetition(passenger))
        }
      }
    case DispatchElevator(id, floor) =>
      if (floorInterval.contains(floor)) {
        val oElevator = elevators.get(id)
        oElevator match {
          case Some(e) =>
            e ! Dispatch(floor)
            sender ! PetitionProcessed
          case None =>
            log.warning(s"$header there is no elevator with id $id")
            sender ! ElevatorDoesNotExist
        }
      } else {
        log.warning(s"$header Dispatch petition is not valid," +
          s" floor $floor is out of range of floors: ${floorInterval}")
        sender ! NoAllowedPetition
      }
  }

  private def bestElevatorToPick(states: List[StateMessage], passenger: Passenger): Option[Int] = {

    def pickNearest(states: List[StateMessage]): Option[Int] = {
      states.map(state => {
        state.id -> Math.abs(state.state.currentFloor - passenger.currentFloor)
      }).sortBy(_._2).headOption.map(_._1)
    }

    // Consider only elevators that can pick passengers
    val (stoppedElevator, elevatorOnMovement) =
      states.filter(_.acceptPassengers).partition(s => s.state.isInstanceOf[Stopped])

    val (onTheWayElevator, elevatorNotOnTheWay) =
      elevatorOnMovement.partition(s => {

        val elevatorState: Moving = s.state.asInstanceOf[Moving]
        elevatorState.movement match {
          case Up => elevatorState.currentFloor < passenger.currentFloor
          case Down => elevatorState.currentFloor > passenger.currentFloor
        }
      })

    val elevatorChosen =
      pickNearest(onTheWayElevator) orElse pickNearest(stoppedElevator) orElse pickNearest(elevatorNotOnTheWay)

    elevatorChosen
  }
}

/**
  * Singleton object that encapsulates protocol messaging and props method for creating the ElevatorSystem actor.
  */
object ElevatorSystem {

  val header: String = "[ELEVATOR-SYSTEM]"

  def props: Props = Props(classOf[ElevatorSystem])

  case object GetSystemStatus

  case class PickUpPetition(passenger: Passenger)

  case class DispatchElevator(id: Int, targetFloor: Int)

  case class SystemStatus(states: List[StateMessage], passenger: Option[Passenger] = None) {
    def toResponse: List[String] =
      "ELEVATOR-ID     | STATE       | ACCEPT PASSENGERS?  " :: states.map(_.toString)
  }

  case object NoAllowedPetition

  case object ElevatorDoesNotExist

  case object PetitionProcessed


}


object ElevatorStatusRetriever {

  case object GetSystemStatus

  def props(originalSender: ActorRef, elevators: List[ActorRef], passenger: Option[Passenger]): Props =
    Props(classOf[ElevatorStatusRetriever], originalSender, elevators, passenger)
}

/**
  * Actor that is created only for retrieving the states from the elevator
  * it will return the result when all the elevators answer with their state
  * to the original sender.
  *
  * Also it is used to query the state of the system when a pickup request arrives
  * and then choose the best option for picking up the passenger.
  */
class ElevatorStatusRetriever(originalSender: ActorRef,
                              elevators: List[ActorRef],
                              passenger: Option[Passenger]) extends Actor with ActorLogging {

  var states: List[StateMessage] = Nil

  override def receive: Receive = {
    case GetSystemStatus =>
      elevators.map(elevator => elevator ! GetState)
    case state: StateMessage =>
      states = state :: states
      collectStates()
  }

  def collectStates() {
    if (states.size == elevators.size) {
      log.info(s"Sending  $states to $originalSender")
      originalSender ! SystemStatus(states.sortBy(_.id), passenger)
      context.stop(self)
    }
  }
}
