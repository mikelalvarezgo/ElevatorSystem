package com.wonderland.elevator_system.infrastructure

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.Eventually
import org.scalatest.{Matchers, WordSpecLike}
import ElevatorSystemSpec.actorSystem
import com.wonderland.elevator_sytem.domain._
import com.wonderland.elevator_sytem.infrastructure.Elevator.{Dispatch, GetState, StateMessage}
import com.wonderland.elevator_sytem.infrastructure.ElevatorSystem
import com.wonderland.elevator_sytem.infrastructure.ElevatorSystem._

class ElevatorSystemSpec extends TestKit(actorSystem) with WordSpecLike with Matchers
  with ImplicitSender with Eventually {

  var elevatorSystem: ActorRef = _
  val elevator1 = TestProbe()
  val elevator2 = TestProbe()
  val elevator3 = TestProbe()
  initElevatorSystem()

  "An elevator system" when {
    "petition of status arrive" should {
      "query all elevator and return response with all states" in {
        elevatorSystem ! GetSystemStatus
        elevator1.expectMsg(GetState)
        elevator1.reply(StateMessage(1, Stopped(0), false))
        elevator2.expectMsg(GetState)
        elevator2.reply(StateMessage(2, Stopped(1), false))
        elevator3.expectMsg(GetState)
        elevator3.reply(StateMessage(3, Stopped(2), false))

        expectMsg(SystemStatus(List(
          StateMessage(1, Stopped(0), false),
          StateMessage(2, Stopped(1), false),
          StateMessage(3, Stopped(2), false)
        )))
      }
    }
    "pickup petition arrive" should {
      "pick the nearest elevator from all elevator" in {
        elevatorSystem ! PickUpPetition(Passenger(3, 1))
        expectMsg(PetitionProcessed)
        elevator1.expectMsg(GetState)
        elevator1.reply(StateMessage(1, Stopped(0)))
        elevator2.expectMsg(GetState)
        elevator2.reply(StateMessage(2, Stopped(1)))
        elevator3.expectMsg(GetState)
        elevator3.reply(StateMessage(3, Stopped(2)))
        elevator3.expectMsg(PickUpPetition(Passenger(3, 1)))
      }
      "pick the nearest elevator that is in the right direction for picking up the passenger" in {
        elevatorSystem ! PickUpPetition(Passenger(3, 1))
        expectMsg(PetitionProcessed)
        elevator1.expectMsg(GetState)
        elevator1.reply(StateMessage(1, Stopped(0)))
        elevator2.expectMsg(GetState)
        elevator2.reply(StateMessage(2, Moving(1, Up)))
        elevator3.expectMsg(GetState)
        elevator3.reply(StateMessage(3, Moving(2, Down)))
        elevator2.expectMsg(PickUpPetition(Passenger(3, 1)))
      }
      "pick the nearest stopped elevator if the moving elevator are going in the opposite direction" in {
        elevatorSystem ! PickUpPetition(Passenger(3, 1))
        expectMsg(PetitionProcessed)
        elevator1.expectMsg(GetState)
        elevator1.reply(StateMessage(1, Stopped(0)))
        elevator2.expectMsg(GetState)
        elevator2.reply(StateMessage(2, Moving(1, Down)))
        elevator3.expectMsg(GetState)
        elevator3.reply(StateMessage(3, Moving(2, Down)))
        elevator1.expectMsg(PickUpPetition(Passenger(3, 1)))
      }

      "pick the nearest elevator if the all elevators are going in the opposite direction" in {
        elevatorSystem ! PickUpPetition(Passenger(3, 1))
        expectMsg(PetitionProcessed)
        elevator1.expectMsg(GetState)
        elevator1.reply(StateMessage(1, Moving(0, Down)))
        elevator2.expectMsg(GetState)
        elevator2.reply(StateMessage(2, Moving(1, Down)))
        elevator3.expectMsg(GetState)
        elevator3.reply(StateMessage(3, Moving(2, Down)))
        elevator3.expectMsg(PickUpPetition(Passenger(3, 1)))
      }

      "try to get an assigned after a period if all elevators are busy" in {

        elevatorSystem ! PickUpPetition(Passenger(3, 1))
        expectMsg(PetitionProcessed)
        elevator1.expectMsg(GetState)
        elevator1.reply(StateMessage(1, Stopped(0), false))
        elevator2.expectMsg(GetState)
        elevator2.reply(StateMessage(2, Stopped(1), false))
        elevator3.expectMsg(GetState)
        elevator3.reply(StateMessage(3, Stopped(2), false))
        // ElevatorSystem will retry pickup after 2 seconds
        elevator1.expectMsg(GetState)
        elevator1.reply(StateMessage(1, Stopped(0), true))
        elevator2.expectMsg(GetState)
        elevator2.reply(StateMessage(2, Stopped(1), false))
        elevator3.expectMsg(GetState)
        elevator3.reply(StateMessage(3, Stopped(2), false))
        elevator1.expectMsg(PickUpPetition(Passenger(3, 1)))

      }
      "should return NoAllowedPetition message if target or current floor are out of the range of floors" in {
        elevatorSystem ! PickUpPetition(Passenger(-1, 11))
        expectMsg(NoAllowedPetition)
      }
    }

    "dispatch petition arrive" should {
      "send dispatch message to target elevator" in {
        elevatorSystem ! DispatchElevator(3, 5)
        expectMsg(PetitionProcessed)
        elevator3.expectMsg(Dispatch(5))
      }
      "send NotAllowedPetition if target floor does not exist" in {
        elevatorSystem ! DispatchElevator(3, 11)
        expectMsg(NoAllowedPetition)
      }

      "send ElevatorDoesNotExist if elevator required does not exist" in {
        elevatorSystem ! DispatchElevator(4, 9)
        expectMsg(ElevatorDoesNotExist)
      }
    }
  }

  private def initElevatorSystem() = {
    elevatorSystem = system.actorOf(Props(new ElevatorSystem {
      override val elevators: Map[Int, ActorRef] = Map(1 -> elevator1.ref, 2 -> elevator2.ref, 3 -> elevator3.ref)
    }))
  }
}

object ElevatorSystemSpec {
  val actorSystem = ActorSystem("clockwork-orange", ConfigFactory.defaultApplication())

}