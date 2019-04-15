package com.wonderland.elevator_sytem.domain

trait ElevatorState {
  val currentFloor: Int
}


case class Stopped(currentFloor: Int) extends ElevatorState {
  override def toString: String = s"Stopped in floor $currentFloor"
}

case class Moving(currentFloor: Int, movement: Movement) extends ElevatorState {
  override def toString: String = s"Moving $movement in floor $currentFloor"
}