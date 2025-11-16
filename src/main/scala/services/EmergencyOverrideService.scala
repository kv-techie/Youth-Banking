package services

import domain._
import java.time.{Instant, Duration}

/** Emergency override window per account. Simple in-memory timer.
  * 
  * Allows parents to temporarily bypass all spending limits for urgent situations.
  * Windows are time-limited and automatically expire.
  */
class EmergencyOverrideService(monitor: MonitoringService) {

  /** Internal window tracking expiration and parent who activated it */
  private case class Window(expiresAt: Instant, parentId: String, reason: String = "")
  
  /** Thread-safe map of active override windows */
  private val windows = scala.collection.concurrent.TrieMap.empty[String, Window]

  /** Enable a timed override for an account
    * 
    * @param accountId The account to enable override for
    * @param parentId The parent authorizing the override
    * @param minutes Duration of override window (default 60 minutes)
    * @param reason Optional reason for the override
    */
  def enableOverride(
    accountId: String, 
    parentId: String, 
    minutes: Long = 60,
    reason: String = "Emergency override"
  ): Unit = {
    val expiresAt = Instant.now().plus(Duration.ofMinutes(minutes))
    val window = Window(expiresAt, parentId, reason)
    windows.update(accountId, window)
    
    monitor.makeAlert(
      accountId, 
      AlertType.EmergencyOverrideUsed, 
      s"🚨 Emergency override active for $minutes minutes. Reason: $reason. Authorized by parent $parentId",
      requiresAction = false
    )
  }

  /** Manually disable override before expiration */
  def disableOverride(accountId: String): Unit = {
    windows.remove(accountId).foreach { window =>
      monitor.makeAlert(
        accountId, 
        AlertType.GenericNotice, 
        s"Emergency override disabled manually. Was authorized by parent ${window.parentId}"
      )
    }
  }

  /** Check if override is currently active and not expired */
  def isActive(accountId: String): Boolean = {
    windows.get(accountId) match {
      case Some(window) if Instant.now().isBefore(window.expiresAt) => 
        true
      case Some(_) => 
        // Window expired, clean up and return false
        windows.remove(accountId)
        monitor.makeAlert(
          accountId,
          AlertType.GenericNotice,
          "Emergency override window expired"
        )
        false
      case None => 
        false
    }
  }

  /** Get remaining time for active override (if any) */
  def getRemainingTime(accountId: String): Option[Duration] = {
    windows.get(accountId).filter(w => Instant.now().isBefore(w.expiresAt)).map { window =>
      Duration.between(Instant.now(), window.expiresAt)
    }
  }

  /** Get details of active override window */
  def getOverrideDetails(accountId: String): Option[OverrideDetails] = {
    windows.get(accountId).filter(w => Instant.now().isBefore(w.expiresAt)).map { window =>
      OverrideDetails(
        accountId = accountId,
        parentId = window.parentId,
        reason = window.reason,
        expiresAt = window.expiresAt,
        remainingMinutes = Duration.between(Instant.now(), window.expiresAt).toMinutes
      )
    }
  }

  /** Extend an existing override window */
  def extendOverride(accountId: String, additionalMinutes: Long): Either[String, Unit] = {
    windows.get(accountId) match {
      case Some(window) if Instant.now().isBefore(window.expiresAt) =>
        val newExpiry = window.expiresAt.plus(Duration.ofMinutes(additionalMinutes))
        val updated = window.copy(expiresAt = newExpiry)
        windows.update(accountId, updated)
        
        monitor.makeAlert(
          accountId,
          AlertType.EmergencyOverrideUsed,
          s"Emergency override extended by $additionalMinutes minutes"
        )
        Right(())
        
      case _ =>
        Left("No active override to extend")
    }
  }

  /** Get all active overrides (for monitoring/admin) */
  def getAllActiveOverrides: Map[String, OverrideDetails] = {
    val now = Instant.now()
    windows.collect {
      case (accountId, window) if now.isBefore(window.expiresAt) =>
        accountId -> OverrideDetails(
          accountId = accountId,
          parentId = window.parentId,
          reason = window.reason,
          expiresAt = window.expiresAt,
          remainingMinutes = Duration.between(now, window.expiresAt).toMinutes
        )
    }.toMap
  }

  /** Clean up all expired overrides (for maintenance) */
  def cleanupExpired(): Int = {
    val now = Instant.now()
    val expired = windows.filter { case (_, window) => now.isAfter(window.expiresAt) }
    expired.foreach { case (accountId, _) => windows.remove(accountId) }
    expired.size
  }
}

/** Details of an active emergency override */
case class OverrideDetails(
  accountId: String,
  parentId: String,
  reason: String,
  expiresAt: Instant,
  remainingMinutes: Long
) {
  override def toString: String = {
    s"""Emergency Override Active:
       |  Account: $accountId
       |  Authorized By: $parentId
       |  Reason: $reason
       |  Expires: $expiresAt
       |  Remaining: $remainingMinutes minutes
       |""".stripMargin
  }
}
