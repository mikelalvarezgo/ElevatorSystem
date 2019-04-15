package com.wonderland.elevator_system.application

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{Matchers, WordSpecLike}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.MediaTypes.`application/json`
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.RouteConcatenation
import com.wonderland.elevator_sytem.application.ElevatorSystemController
import com.wonderland.elevator_sytem.domain.Stopped
import com.wonderland.elevator_sytem.infrastructure.Elevator.StateMessage
import com.wonderland.elevator_sytem.infrastructure.ElevatorSystem.SystemStatus
import akka.util.Timeout
import scala.concurrent.duration._
import spray.json.DefaultJsonProtocol

class ElevatorSystemControllerSpec extends WordSpecLike with Matchers
  with ScalatestRouteTest with SprayJsonSupport with DefaultJsonProtocol with RouteConcatenation {

  implicit val ec = system.dispatcher
  implicit val timeout: Timeout = 10.seconds
  val controller = new ElevatorSystemController(system)
  val route = controller.routes

  "An ElevatorSystemController" when {
    "GET petition for status arrives" should {
      "return the states of all elevators" in {
        val response = SystemStatus(List(
          StateMessage(1, Stopped(0)),
          StateMessage(2, Stopped(0)),
          StateMessage(3, Stopped(0))
        ))
        val expectedResponse = response.toResponse

        Get("/elevatorSystem/status") ~> route ~> check {

          status shouldBe (OK)
          responseAs[List[String]] shouldBe expectedResponse
        }
      }
    }

      "POST petition for request an elevator arrives" should {
        "process the request if it is correct format" in {

          Post("/elevatorSystem/pickup", jsonEntity(
            s"""
               |{
               |    "currentFloor" : 1,
               |    "targetFloor": 3
               |}
        """.stripMargin)) ~> route ~> check {
            status shouldBe Accepted
          }
        }

        "return BadRequest if the request contain non existent currentFloor" in {
          Post("/elevatorSystem/pickup", jsonEntity(
            s"""
               |{
               |    "currentFloor" : 11,
               |    "targetFloor": 3
               |}
        """.stripMargin)) ~> route ~> check {
            status shouldBe BadRequest
          }
        }

        "return BadRequest if the request contain non existent targetFloor" in {
          Post("/elevatorSystem/pickup", jsonEntity(
            s"""
               |{
               |    "currentFloor" : 3,
               |    "targetFloor": 11
               |}
        """.stripMargin)) ~> route ~> check {
            status shouldBe BadRequest
          }
        }
      }

      "POST petition for update an elevator arrives" should {
        "return accepted if payload in correct format" in {
          Post("/elevatorSystem/update", jsonEntity(
            s"""
               |{
               |    "elevatorId" : 2,
               |    "targetFloor": 1
               |}
        """.stripMargin)) ~> route ~> check {
            status shouldBe Accepted
          }
        }
        "return NotFound if elevator does not exist" in {
          Post("/elevatorSystem/update", jsonEntity(
            s"""
               |{
               |    "elevatorId" : 12,
               |    "targetFloor": 1
               |}
        """.stripMargin)) ~> route ~> check {
            status shouldBe NotFound
          }
        }

        "return BadRequest if incorrect targetFloor" in {
          Post("/elevatorSystem/update", jsonEntity(
            s"""
               |{
               |    "elevatorId" : 1,
               |    "targetFloor": 11
               |}
        """.stripMargin)) ~> route ~> check {
            status shouldBe BadRequest
          }
        }
      }
  }

  private def jsonEntity(json: String) = HttpEntity(`application/json`, json)

}