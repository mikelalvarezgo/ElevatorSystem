package com.wonderland.elevator_system.infrastructure

import ElevatorSpec.actorSystem
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.wonderland.elevator_sytem.domain._
import com.wonderland.elevator_sytem.infrastructure.{Elevator, SystemSettings}
import com.wonderland.elevator_sytem.infrastructure.Elevator._
import com.wonderland.elevator_sytem.infrastructure.ElevatorSystem.PickUpPetition
import org.scalatest.{Matchers, WordSpecLike}
import org.scalatest.concurrent.Eventually

import scala.concurrent.Await
import scala.concurrent.duration._

class ElevatorSpec extends TestKit(actorSystem) with WordSpecLike with Matchers
  with ImplicitSender  with Eventually {

  val systemSettings = SystemSettings(system)
  implicit val timeout: Timeout = 15.seconds
  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 10.seconds, interval = 700.millis)


  "An elevator" when {
    "a GetState message arrives " should {
      "answer with the state message " in {
        val elevator = anElevator
        elevator ! GetState
        expectMsg(StateMessage(1, Stopped(0)))
      }
    }

    "a RequestElevator message arrives" should {
      "pick up one passenger and stop in the target floor" in {
        val elevator = anElevator
        elevator ! PickUpPetition(Passenger(3, 0))
        eventually(getElevatorState(elevator)
          shouldBe InternalState(State(0, Set(Passenger(3, 0)), Set.empty), Some(Up)))
        eventually(getElevatorState(elevator)
          shouldBe InternalState(State(1, Set(Passenger(3, 0)), Set.empty), Some(Up)))
        eventually(getElevatorState(elevator)
          shouldBe InternalState(State(2, Set(Passenger(3, 0)), Set.empty), Some(Up)))
        eventually(getElevatorState(elevator)
          shouldBe InternalState(State(3, Set.empty, Set(Passenger(3, 0))), Some(Down)))
        eventually(getElevatorState(elevator)
          shouldBe InternalState(State(2, Set.empty, Set(Passenger(3, 0))), Some(Down)))
        eventually(getElevatorState(elevator)
          shouldBe InternalState(State(1, Set.empty, Set(Passenger(3, 0))), Some(Down)))
        eventually(getElevatorState(elevator)
          shouldBe InternalState(State(0, Set.empty, Set.empty), None))
      }

      "visit all the needed floors in one direction before changing of direction" in {
        val elevator = anElevator

        elevator ! PickUpPetition(Passenger(2, 0))
        elevator ! PickUpPetition(Passenger(1, 3))

        eventually(getElevatorState(elevator) shouldBe
          InternalState(State(0, Set(Passenger(2, 0), Passenger(1, 3)), Set.empty), Some(Up)))
        eventually(getElevatorState(elevator) shouldBe
          InternalState(State(1, Set(Passenger(2, 0)), Set(Passenger(1, 3))), Some(Up)))
        eventually(getElevatorState(elevator) shouldBe
          InternalState(State(2, Set.empty, Set(Passenger(1, 3), Passenger(2, 0))), Some(Up)))
        eventually(getElevatorState(elevator) shouldBe
          InternalState(State(3, Set.empty, Set(Passenger(2, 0))), Some(Down)))
        eventually(getElevatorState(elevator) shouldBe
          InternalState(State(2, Set.empty, Set(Passenger(2, 0))), Some(Down)))
        eventually(getElevatorState(elevator) shouldBe
          InternalState(State(1, Set.empty, Set(Passenger(2, 0))), Some(Down)))
        eventually(getElevatorState(elevator) shouldBe
          InternalState(State(0, Set.empty, Set.empty), None))
      }
    }

    "dispatch message arrives" should {
      "be dispatched to the requested floor it doesn't have nothing to do" in {

        val elevator = anElevator

        elevator ! Dispatch(3)
        eventually(getElevatorState(elevator) shouldBe InternalState(State(0, Set.empty, Set.empty), Some(Up)))
        eventually(getElevatorState(elevator) shouldBe InternalState(State(1, Set.empty, Set.empty), Some(Up)))
        eventually(getElevatorState(elevator) shouldBe InternalState(State(2, Set.empty, Set.empty), Some(Up)))
        eventually(getElevatorState(elevator) shouldBe InternalState(State(3, Set.empty, Set.empty), None))
      }

      "be dispatched to the last requested floor it doesn't have nothing to do " in {
        val elevator = anElevator
        elevator ! Dispatch(3)
        elevator ! Dispatch(5)
        eventually(getElevatorState(elevator) shouldBe InternalState(State(0, Set.empty, Set.empty), Some(Up)))
        eventually(getElevatorState(elevator) shouldBe InternalState(State(1, Set.empty, Set.empty), Some(Up)))
        eventually(getElevatorState(elevator) shouldBe InternalState(State(2, Set.empty, Set.empty), Some(Up)))
        eventually(getElevatorState(elevator) shouldBe InternalState(State(3, Set.empty, Set.empty), Some(Up)))
        eventually(getElevatorState(elevator) shouldBe InternalState(State(4, Set.empty, Set.empty), Some(Up)))
        eventually(getElevatorState(elevator) shouldBe InternalState(State(5, Set.empty, Set.empty), None))
      }

      "process previous request and then be dispatched to requested floor" in {
        val elevator = anElevator
        elevator ! PickUpPetition(Passenger(2, 0))
        elevator ! Dispatch(3)
        eventually(getElevatorState(elevator)
          shouldBe InternalState(State(0, Set(Passenger(2, 0)), Set.empty), Some(Up)))
        eventually(getElevatorState(elevator)
          shouldBe InternalState(State(1, Set(Passenger(2, 0)), Set.empty), Some(Up)))
        eventually(getElevatorState(elevator)
          shouldBe InternalState(State(2, Set.empty, Set(Passenger(2, 0))), Some(Down)))
        eventually(getElevatorState(elevator)
          shouldBe InternalState(State(1, Set.empty, Set(Passenger(2, 0))), Some(Down)))
        eventually(getElevatorState(elevator)
          shouldBe InternalState(State(0, Set.empty, Set.empty), Some(Up)))
        eventually(getElevatorState(elevator)
          shouldBe InternalState(State(1, Set.empty, Set.empty), Some(Up)))
        eventually(getElevatorState(elevator)
          shouldBe InternalState(State(2, Set.empty, Set.empty), Some(Up)))
        eventually(getElevatorState(elevator)
          shouldBe InternalState(State(3, Set.empty, Set.empty), None))
      }
    }
  }

  private def getElevatorState(elevator:ActorRef)(implicit timeout: Timeout): InternalState = {
    val fResult = (elevator ? GetInternState).mapTo[InternalState]
    Await.result(fResult, 1.second)
  }

  private def anElevator: ActorRef = system.actorOf(Elevator.props(1))

}

object ElevatorSpec {
  val actorSystem = ActorSystem("clockwork-orange", ConfigFactory.defaultApplication())
}
