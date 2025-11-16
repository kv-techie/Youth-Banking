package utils

import java.time.{LocalTime, ZoneId, ZonedDateTime}

/** Utility to check day/night mode */
object TimeUtils {
  private val DayStart: LocalTime = LocalTime.of(7, 0)
  private val DayEnd: LocalTime   = LocalTime.of(21, 0)

  /** Returns true if the current time is considered "day" */
  def isDayTime(zone: ZoneId = ZoneId.systemDefault()): Boolean = {
    val now = ZonedDateTime.now(zone).toLocalTime
    !now.isBefore(DayStart) && !now.isAfter(DayEnd)
  }

  /** Returns true if the current time is "night" */
  def isNightTime(zone: ZoneId = ZoneId.systemDefault()): Boolean = !isDayTime(zone)
}
