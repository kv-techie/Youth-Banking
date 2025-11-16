package services

import domain._
import java.time.{Instant, Duration}

/** Emergency override window per account. Simple in-memory timer. */
class EmergencyOverrideService(monitor: MonitoringService) {

  private case class Window(expiresAt: Instant, parentId: String)
  private val windows = scala.collection.concurrent.TrieMap.empty[String, Window]

  /** Enable a timed override for an account (minutes duration) */
  def enableOverride(accountId: String, parentId: String, minutes: Long = 60): Unit = {
    val w = Window(Instant.now().plus(Duration.ofMinutes(minutes)), parentId)
    windows.update(accountId, w)
    monitor.makeAlert(accountId, AlertType.EmergencyOverrideUsed, s"Emergency override active for $minutes minutes by parent=$parentId")
  }

  def disableOverride(accountId: String): Unit = {
    windows.remove(accountId)
    monitor.makeAlert(accountId, AlertType.GenericNotice, s"Emergency override disabled for account=$accountId")
  }

  def isActive(accountId: String): Boolean = {
    windows.get(accountId) match
      case Some(w) if Instant.now().isBefore(w.expiresAt) => true
      case Some(_) => windows.remove(accountId); false
      case None => false
  }
}
