package services

import domain._
import java.time.Instant

/** MonitoringService — collects alerts and exposes helper methods. */
class MonitoringService {
  def pushAlert(alert: Alert): Unit = {
    Repository.addAlert(alert.accountId, alert)
    // In real system: push notifications, SMS, email, etc.
    println(s"[ALERT] ${alert.alertType} for account=${alert.accountId}: ${alert.message}")
  }

  def makeAlert(accountId: String, t: AlertType, message: String, meta: Map[String, String] = Map.empty): Alert = {
    val a = Alert(accountId = accountId, alertType = t, message = message, metadata = meta, createdAt = Instant.now())
    pushAlert(a)
    a
  }
}
