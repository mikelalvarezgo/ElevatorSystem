package com.wonderland.elevator_sytem.application

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

trait Protocol extends DefaultJsonProtocol with SprayJsonSupport {

  case class PickupPayload(currentFloor: Int, targetFloor: Int)

  case class UpdatePayload(elevatorId: Int, targetFloor: Int)

  implicit val pickupJsonFormat = jsonFormat2(PickupPayload)
  implicit val updateJsonFormat = jsonFormat2(UpdatePayload)
}


