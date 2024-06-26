package com.resy

import akka.actor.ActorSystem
import org.apache.logging.log4j.scala.Logging
import org.joda.time.DateTime
import pureconfig.ConfigSource
import pureconfig.generic.auto._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object ResyBookingBot extends Logging {

  def main(args: Array[String]): Unit = {
    logger.info("Starting Resy Booking Bot")

    val resyConfig        = ConfigSource.resources("copy.conf")
    val resyKeys          = resyConfig.at("resyKeys").loadOrThrow[ResyKeys]
    val additionalHeaders = resyConfig.at("additionalHeaders").loadOrThrow[AdditionalHeaders]
    val resDetails        = resyConfig.at("resDetails").loadOrThrow[ReservationDetails]
    val snipeTime         = resyConfig.at("snipeTime").loadOrThrow[SnipeTime]

    val resyApi             = new ResyApi(resyKeys, additionalHeaders)
    val resyClient          = new ResyClient(resyApi)
    val resyBookingWorkflow = new ResyBookingWorkflow(resyClient, resDetails)

    val system      = ActorSystem("System")
    val dateTimeNow = DateTime.now
    val todaysSnipeTime = dateTimeNow
      .withHourOfDay(snipeTime.hours)
      .withMinuteOfHour(snipeTime.minutes)
      .withSecondOfMinute(0)
      .withMillisOfSecond(0)

    val nextSnipeTime =
      if (todaysSnipeTime.getMillis > dateTimeNow.getMillis) todaysSnipeTime
      else todaysSnipeTime.plusDays(1)

    val millisUntilTomorrow = nextSnipeTime.getMillis - DateTime.now.getMillis - 50

    logger.info(s"Next snipe time: $nextSnipeTime")

    system.scheduler.scheduleOnce(millisUntilTomorrow millis) {
      resyBookingWorkflow.run()

      logger.info("Shutting down Resy Booking Bot")
      System.exit(0)
    }
  }
}
