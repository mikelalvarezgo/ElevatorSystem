package com.wonderland.elevator_sytem.domain

case class Passenger(currentFloor: Int, targetFloor: Int) {
  def travelingDirection: Movement = Movement(currentFloor, targetFloor)
}
