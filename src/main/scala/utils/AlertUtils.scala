package utils

import domain.Alert

/** Utility to create alerts */
object AlertUtils {

  def createAlert(message: String, level: String = "INFO"): Alert =
    Alert(message = message, level = level, timestamp = java.time.Instant.now())

  def highRiskAlert(msg: String): Alert =
    createAlert(msg, "HIGH")

  def mediumRiskAlert(msg: String): Alert =
    createAlert(msg, "MEDIUM")

  def info(msg: String): Alert =
    createAlert(msg, "INFO")
}
