package services

import domain._
import scala.collection.mutable
import java.time.{Duration, Instant}

/** In-memory storage for demo purposes.
  * In production, replace with a real database.
  */
object Repository {
  private val accounts = mutable.Map.empty[String, Account]
  private val alerts = mutable.Map.empty[String, List[Alert]]
  private val baselines = mutable.Map.empty[String, BehaviorBaseline]
  private val patterns = mutable.Map.empty[String, List[BehaviorPattern]]

  // ===== Account Operations =====
  
  def getAccount(id: String): Option[Account] = accounts.get(id)
  
  def saveAccount(acc: Account): Unit = accounts.put(acc.id, acc)
  
  def getAllAccounts: List[Account] = accounts.values.toList

  def deleteAccount(id: String): Option[Account] = accounts.remove(id)

  // ===== Alert Operations =====
  
  def getAlerts(accountId: String): List[Alert] = alerts.getOrElse(accountId, Nil)
  
  def saveAlert(accountId: String, alert: Alert): Unit = {
    val current = alerts.getOrElse(accountId, Nil)
    alerts.put(accountId, alert :: current)
  }

  def clearAlerts(accountId: String): Unit = alerts.remove(accountId)

  // ===== Baseline Operations (for AI/ML) =====
  
  def getBaseline(accountId: String): Option[BehaviorBaseline] = baselines.get(accountId)
  
  def saveBaseline(baseline: BehaviorBaseline): Unit = {
    baselines.put(baseline.accountId, baseline)
  }

  def deleteBaseline(accountId: String): Option[BehaviorBaseline] = baselines.remove(accountId)

  def getAllBaselines: List[BehaviorBaseline] = baselines.values.toList

  // ===== Pattern Operations (for AI/ML) =====
  
  def getPatterns(accountId: String, duration: Duration): List[BehaviorPattern] = {
    val cutoff = Instant.now().minus(duration)
    patterns.getOrElse(accountId, Nil).filter(_.lastDetected.isAfter(cutoff))
  }

  def getAllPatterns(accountId: String): List[BehaviorPattern] = {
    patterns.getOrElse(accountId, Nil)
  }
  
  def savePattern(accountId: String, pattern: BehaviorPattern): Unit = {
    val current = patterns.getOrElse(accountId, Nil)
    
    // Merge if same pattern type exists recently (within 24 hours)
    val existing = current.find(p => 
      p.patternType == pattern.patternType && 
      Duration.between(p.lastDetected, pattern.lastDetected).toHours < 24
    )
    
    existing match
      case Some(p) =>
        // Merge with existing pattern
        val updated = p.copy(
          severity = (p.severity + pattern.severity) / 2, // Average severity
          occurrences = p.occurrences + pattern.occurrences,
          lastDetected = pattern.lastDetected,
          metadata = p.metadata ++ pattern.metadata // Merge metadata
        )
        patterns.put(accountId, updated :: current.filterNot(_ == p))
      case None =>
        // Add new pattern
        patterns.put(accountId, pattern :: current)
  }

  def clearPatterns(accountId: String): Unit = patterns.remove(accountId)

  def clearOldPatterns(accountId: String, olderThan: Duration): Unit = {
    val cutoff = Instant.now().minus(olderThan)
    patterns.get(accountId).foreach { current =>
      val filtered = current.filter(_.lastDetected.isAfter(cutoff))
      if (filtered.isEmpty) patterns.remove(accountId)
      else patterns.put(accountId, filtered)
    }
  }

  // ===== Utility Operations =====

  /** Clear all data (useful for testing) */
  def clear(): Unit = {
    accounts.clear()
    alerts.clear()
    baselines.clear()
    patterns.clear()
  }

  /** Clear only AI/ML related data */
  def clearAIData(): Unit = {
    baselines.clear()
    patterns.clear()
  }

  /** Get statistics for monitoring */
  def getStats: RepositoryStats = {
    RepositoryStats(
      totalAccounts = accounts.size,
      totalAlerts = alerts.values.map(_.size).sum,
      totalBaselines = baselines.size,
      totalPatterns = patterns.values.map(_.size).sum,
      accountsWithBaselines = accounts.keys.count(id => baselines.contains(id)),
      accountsWithPatterns = accounts.keys.count(id => patterns.contains(id))
    )
  }

  /** Cleanup old data periodically */
  def cleanup(retentionDays: Int = 90): Unit = {
    val cutoff = Instant.now().minusSeconds(retentionDays * 86400L)
    
    // Clean old alerts
    alerts.foreach { case (accountId, alertList) =>
      val filtered = alertList.filter(_.timestamp.isAfter(cutoff))
      if (filtered.isEmpty) alerts.remove(accountId)
      else alerts.put(accountId, filtered)
    }
    
    // Clean old patterns
    patterns.foreach { case (accountId, patternList) =>
      val filtered = patternList.filter(_.lastDetected.isAfter(cutoff))
      if (filtered.isEmpty) patterns.remove(accountId)
      else patterns.put(accountId, filtered)
    }
  }
}

/** Statistics about repository data */
case class RepositoryStats(
  totalAccounts: Int,
  totalAlerts: Int,
  totalBaselines: Int,
  totalPatterns: Int,
  accountsWithBaselines: Int,
  accountsWithPatterns: Int
) {
  override def toString: String = {
    s"""Repository Statistics:
       |  Accounts: $totalAccounts
       |  Alerts: $totalAlerts
       |  Baselines: $totalBaselines (${accountsWithBaselines} accounts)
       |  Patterns: $totalPatterns (${accountsWithPatterns} accounts)
       |""".stripMargin
  }
}
