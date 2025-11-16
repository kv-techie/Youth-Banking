package services

import domain._
import java.time.Instant

/** Monitoring service for tracking alerts and notifications
  * 
  * Provides centralized alert management for all safety features
  */
class MonitoringService {
  
  /** Create and save an alert */
  def makeAlert(
    accountId: String, 
    alertType: AlertType, 
    message: String, 
    riskScore: Option[RiskScore] = None, 
    requiresAction: Boolean = false
  ): Unit = {
    val alert = Alert(
      accountId = accountId,
      alertType = alertType,
      message = message,
      timestamp = Instant.now(),
      riskScore = riskScore,
      requiresAction = requiresAction
    )
    Repository.saveAlert(accountId, alert)
  }
  
  /** Send an alert (alias for makeAlert for backward compatibility) */
  def sendAlert(accountId: String, alert: Alert): Unit = {
    Repository.saveAlert(accountId, alert)
  }
  
  /** Create an alert object without saving */
  def createAlert(
    accountId: String,
    alertType: AlertType,
    message: String,
    riskScore: Option[RiskScore] = None,
    requiresAction: Boolean = false
  ): Alert = {
    Alert(
      accountId = accountId,
      alertType = alertType,
      message = message,
      timestamp = Instant.now(),
      riskScore = riskScore,
      requiresAction = requiresAction
    )
  }
  
  /** Get all alerts for an account */
  def getAlerts(accountId: String): List[Alert] = {
    Repository.getAlerts(accountId)
  }
  
  /** Get high priority alerts */
  def getHighPriorityAlerts(accountId: String): List[Alert] = {
    Repository.getAlerts(accountId).filter(_.isHighPriority)
  }
  
  /** Get alerts that require action */
  def getActionRequiredAlerts(accountId: String): List[Alert] = {
    Repository.getAlerts(accountId).filter(_.requiresAction)
  }
  
  /** Clear all alerts for an account */
  def clearAlerts(accountId: String): Unit = {
    Repository.clearAlerts(accountId)
  }
  
  /** Count unread alerts */
  def countUnreadAlerts(parentId: String, accountId: String): Int = {
    Repository.getAlerts(accountId).count { alert =>
      !Repository.isAlertRead(parentId, accountId, alert)
    }
  }
}
