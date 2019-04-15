package com.wonderland.elevator_sytem.domain

sealed trait Movement  {
  val opposite: Movement
}

case object Up extends Movement {
  val opposite = Down
}

case object Down extends Movement {
  override val opposite: Movement = Up
}


object Movement {

  def apply(currentFloor: Int, targetFloor: Int): Movement =
    if (currentFloor < targetFloor) Up else Down

}