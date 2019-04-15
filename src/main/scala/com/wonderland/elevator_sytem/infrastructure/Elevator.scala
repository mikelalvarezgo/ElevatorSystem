package com.wonderland.elevator_sytem.infrastructure

import akka.actor.{Actor, ActorLogging, Props}
import com.wonderland.elevator_sytem.domain._
import com.wonderland.elevator_sytem.infrastructure.Elevator._
import com.wonderland.elevator_sytem.infrastructure.ElevatorSystem.PickUpPetition

import scala.concurrent.duration._

/*
  This actor represents the elevator.

  The actor has three states:
    - Stopped: The actor is stop in a floor, waiting for a pickUp request or an update.
    - moving: The actor is moving for picking up passengers and release them in them target floor.
    - dispatching: The actor has received an update for moving to an specific floor, it will finish it previous job and
      move to that floor. In this state it won't accept more pickup request in order to process update request as soon
      as possible.

      @author Mikel Alvarez (mikelalvarezgo@gmail.com)
 */
class Elevator(elevatorId: Int) extends Actor with ActorLogging {

  val systemSettings = SystemSettings(context.system)
  implicit val executionContext = context.system.dispatcher

  def moveOneFloor: Unit = {
    val selfCapt = self
    context.system.scheduler.scheduleOnce(1.second, selfCapt, MoveOneFloor)
  }
  override def receive: Receive = stopped(State(0, Set.empty, Set.empty))

  def stopped(state: State): Receive = {
    case GetState =>
      log.debug(s"${header(elevatorId)} Sending state to supervisor : Stopped at floor ${state.currentFloor}")
      sender ! StateMessage(elevatorId, Stopped(state.currentFloor))

    case GetInternState =>
      sender ! InternalState(state)

    case PickUpPetition(passenger) =>
      val movement = Movement(state.currentFloor, passenger.currentFloor)
      log.info(s"${header(elevatorId)} Processing petition from $passenger" +
        s" and starting to move $movement")
      val newState = state.processPickUpRequest(passenger)
      context.become(moving(newState, movement))
      moveOneFloor

    case Dispatch(targetFloor) =>
      if (targetFloor == state.currentFloor)
        log.info(s"${header(elevatorId)} Nothing to update, elevator is already in floor $targetFloor")
      else {
        log.info(s"${header(elevatorId)} Dispatching elevator to floor $targetFloor")
        context.become(dispatching(state, Movement.apply(state.currentFloor, targetFloor), targetFloor))
        moveOneFloor
      }
  }

  def moving(state: State, movement: Movement): Receive = {
    case GetState =>
      log.info(s"${header(elevatorId)} Sending state to supervisor : Moving $movement at floor ${state.currentFloor}")
      sender ! StateMessage(elevatorId, Moving(state.currentFloor, movement))

    case GetInternState =>
      sender ! InternalState(state, Some(movement))

    case PickUpPetition(passenger) =>
      val newState = state.processPickUpRequest(passenger)
      context.become(moving(newState, movement))

    case MoveOneFloor =>
      val newState = UpdateStateWhenNewFloorReached(state, movement, elevatorId)
      transitionToNextState(newState, movement, elevatorId)

    case Dispatch(targetFloor) =>
      log.info(s"${header(elevatorId)} Receiving request for dispatching elevator to floor $targetFloor")
      context.become(dispatching(state, movement, targetFloor))

    case Restart => context.become(stopped(state = State(0, Set.empty, Set.empty)))
  }

  def dispatching(state: State, movement: Movement, targetFloor: Int): Receive = {
    case GetState =>
      log.debug(s"${header(elevatorId)} Sending state to supervisor : Dispatching to floor $targetFloor")
      sender ! StateMessage(elevatorId, Moving(state.currentFloor, movement), false)

    case GetInternState =>
      sender ! InternalState(state, Some(movement))

    case MoveOneFloor =>
      val newState = UpdateStateWhenNewFloorReached(state, movement, elevatorId)
      transitionToNextState(newState, movement, elevatorId, Some(targetFloor))

    case Dispatch(newTargetFloor) =>
      log.debug(s"${header(elevatorId)} Receiving a new request for dispatching elevator " +
        s"to floor $newTargetFloor, previous request for floor $targetFloor will be replaced")
      context.become(dispatching(state, movement, newTargetFloor))
  }

  private def UpdateStateWhenNewFloorReached(state: State, movement: Movement, elevatorId: Int): State = {
    val stateWithNewFloor = state.move(movement)
    log.debug(s"${header(elevatorId)} Arrived to floor ${stateWithNewFloor.currentFloor} !")
    val oPassengerToPickUp = stateWithNewFloor.getPassengerToPickUp
    val oPassengerToLeave = stateWithNewFloor.getPassengerToLeave

    (oPassengerToPickUp, oPassengerToLeave) match {
      case (Some(pickUp), Some(leave)) =>
        log.info(s"${header(elevatorId)} Picking passenger $pickUp " +
          s"and leaving passenger $leave in floor ${stateWithNewFloor.currentFloor}")
        stateWithNewFloor.pickUpPassenger(pickUp).leavePassenger(leave)
      case (Some(pickUp), None) =>
        log.info(s"${header(elevatorId)} Picking passenger $pickUp in floor ${stateWithNewFloor.currentFloor}")
        stateWithNewFloor.pickUpPassenger(pickUp)
      case (None, Some(leave)) =>
        log.info(s"${header(elevatorId)} Leaving passenger $leave in floor ${stateWithNewFloor.currentFloor}")
        stateWithNewFloor.leavePassenger(leave)
      case _ =>
        log.info(s"${header(elevatorId)} Nothing to do in floor ${stateWithNewFloor.currentFloor}")
        stateWithNewFloor
    }
  }

  private def transitionToNextState(newState: State,
                                    movement: Movement,
                                    elevatorId: Int,
                                    dispatchedFloor: Option[Int] = None) = {
    val nextFloorsToVisit =
      (newState.passengersToPickUp.map(_.currentFloor) ++ newState.passengerInside.map(_.targetFloor))

    if (nextFloorsToVisit.isEmpty) {
      val shouldStop = dispatchedFloor.map(f => f == newState.currentFloor).getOrElse(true)
      if (shouldStop)
        context.become(stopped(newState))
      else {
        log.info(s"${header(elevatorId)} Previous requests have been treated, dispatching to ${dispatchedFloor.get}")
        context.become(dispatching(newState, Movement(newState.currentFloor, dispatchedFloor.get), dispatchedFloor.get))
        moveOneFloor
      }
    } else {
      val canContinueInSameDirection = movement match {
        case Up => newState.currentFloor < nextFloorsToVisit.max
        case Down => newState.currentFloor > nextFloorsToVisit.min
      }
      val newMovement = if (canContinueInSameDirection) movement else movement.opposite
      dispatchedFloor.map(f => context.become(dispatching(newState, newMovement, f)))
        .getOrElse(context.become(moving(newState, newMovement)))
      moveOneFloor
    }
  }
}

/**
  * Singleton object that encapsulates protocol messaging and props method for creating the Elevator actor.
  */
object Elevator {

  val header: Int => String = id => s"[ELEVATOR-$id]"

  def props(elevatorId: Int): Props = Props(classOf[Elevator], elevatorId)

  /* This case class represent the internal state of the elevator:
        -currentFloor: Last floor achieved by the elevator.
        -passengersToPickUp: Passenger that sent a request and that must be picked up by this elevator
        -passengersInside: Passengers inside the elevator waitint to be released
   */
  case class State(currentFloor: Int, passengersToPickUp: Set[Passenger], passengerInside: Set[Passenger]) {
    def processPickUpRequest(passenger: Passenger): State =
      if (currentFloor == passenger.currentFloor)
        copy(passengerInside = passengerInside + passenger)
      else
        copy(passengersToPickUp = passengersToPickUp + passenger)

    def pickUpPassenger(passenger: Passenger): State = copy(
      passengerInside = passengerInside + passenger,
      passengersToPickUp = passengersToPickUp - passenger
    )

    def leavePassenger(passenger: Passenger): State = copy(
      passengerInside = passengerInside - passenger
    )

    def getPassengerToPickUp: Option[Passenger] = passengersToPickUp.find(p => p.currentFloor == currentFloor)

    def getPassengerToLeave: Option[Passenger] = passengerInside.find(_.targetFloor == currentFloor)

    def move(movement: Movement): State = movement match {
      case Down => copy(currentFloor = currentFloor - 1)
      case _ => copy(currentFloor = currentFloor + 1)
    }
  }

  case class StateMessage(id: Int, state: ElevatorState, acceptPassengers: Boolean = true)  {
    override def toString: String = s"$id |  $state | $acceptPassengers"
  }

  case class InternalState(state: State, movement: Option[Movement] = None)

  case object MoveOneFloor

  case object GetState

  case object GetInternState // created only for testing

  case object Restart

  case class Dispatch(targetFloor: Int)

}
