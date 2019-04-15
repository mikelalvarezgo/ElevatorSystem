package com.wonderland.elevator_sytem.infrastructure

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import com.typesafe.config.Config
import scala.concurrent.duration._

class SystemSettings(config: Config) {

  val numberOfFloors: Int = config.getInt("number_of_floors")

  val floorTimeTravel: FiniteDuration = config.getDuration("floor_time_travel", TimeUnit.SECONDS).seconds

  val numberOfElevator: Int = config.getInt("number_of_elevators")

  val pickUpRetryPeriod: FiniteDuration = config.getDuration("pickup_retry_period", TimeUnit.SECONDS).seconds

  val port: Int = config.getInt("port")

}

object SystemSettings {

  def apply(system: ActorSystem): SystemSettings =
    new SystemSettings(system.settings.config.getConfig("elevator_system"))
}