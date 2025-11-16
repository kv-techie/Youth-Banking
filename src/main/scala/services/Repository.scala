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
  
  // Automation-related storage
  private val automationSchedules = mutable.Map.empty[String, AutomationSchedule]
  private val originalLimits = mutable.Map.empty[String, Limits]
  private val scheduledRestores = mutable.Map.empty[String, Instant]
  private val categoryRestrictions = mutable.Map.empty[String, (Set[Category], Instant)]
  private val executionLogs = mutable.Map.empty[String, List[ScheduleExecutionLog]]

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

  // ===== Automation Schedule Operations =====
  
  def getAutomationSchedule(accountId: String): Option[AutomationSchedule] = 
    automationSchedules.get(accountId)
  
  def saveAutomationSchedule(schedule: AutomationSchedule): Unit = 
    automationSchedules.put(schedule.accountId, schedule)
  
  def deleteAutomationSchedule(accountId: String): Unit = 
    automationSchedules.remove(accountId)

  def getAllAutomationSchedules: List[AutomationSchedule] = 
    automationSchedules.values.toList

  // ===== Original Limits Operations (for restoration) =====
  
  def saveOriginalLimits(accountId: String, limits: Limits): Unit = 
    originalLimits.put(accountId, limits)
  
  def getOriginalLimits(accountId: String): Option[Limits] = 
    originalLimits.get(accountId)
  
  def clearOriginalLimits(accountId: String): Unit = 
    originalLimits.remove(accountId)

  // ===== Scheduled Restore Operations =====
  
  def saveScheduledRestore(accountId: String, restoreAt: Instant): Unit = 
    scheduledRestores.put(accountId, restoreAt)
  
  def getScheduledRestore(accountId: String): Option[Instant] = 
    scheduledRestores.get(accountId)
  
  def getAllScheduledRestores(): Map[String, Instant] = 
    scheduledRestores.toMap
  
  def clearScheduledRestore(accountId: String): Unit = 
    scheduledRestores.remove(accountId)

  // ===== Category Restriction Operations =====
  
  def saveCategoryRestriction(accountId: String, categories: Set[Category], expiresAt: Instant): Unit = 
    categoryRestrictions.put(accountId, (categories, expiresAt))
  
  def getCategoryRestriction(accountId: String): Option[(Set[Category], Instant)] = 
    categoryRestrictions.get(accountId)
  
  def getAllCategoryRestrictions(): Map[String, (Set[Category], Instant)] = 
    categoryRestrictions.toMap
  
  def clearCategoryRestriction(accountId: String): Unit = 
    categoryRestrictions.remove(accountId)

  // ===== Execution Log Operations =====
  
  def saveExecutionLog(log: ScheduleExecutionLog): Unit = {
    val current = executionLogs.getOrElse(log.accountId, Nil)
    executionLogs.put(log.accountId, log :: current)
  }
  
  def getExecutionLogs(accountId: String): List[ScheduleExecutionLog] = 
    executionLogs.getOrElse(accountId, Nil)

  def clearExecutionLogs(accountId: String): Unit = 
    executionLogs.remove(accountId)

  // ===== Utility Operations =====

  /** Clear all data (useful for testing) */
  def clear(): Unit = {
    accounts.clear()
    alerts.clear()
    baselines.clear()
    patterns.clear()
    automationSchedules.clear()
    originalLimits.clear()
    scheduledRestores.clear()
    categoryRestrictions.clear()
    executionLogs.clear()
  }

  /** Clear only AI/ML related data */
  def clearAIData(): Unit = {
    baselines.clear()
    patterns.clear()
  }

  /** Clear only automation-related data */
  def clearAutomationData(): Unit = {
    automationSchedules.clear()
    originalLimits.clear()
    scheduledRestores.clear()
    categoryRestrictions.clear()
    executionLogs.clear()
  }

  /** Get statistics for monitoring */
  def getStats: RepositoryStats = {
    RepositoryStats(
      totalAccounts = accounts.size,
      totalAlerts = alerts.values.map(_.size).sum,
      totalBaselines = baselines.size,
      totalPatterns = patterns.values.map(_.size).sum,
      accountsWithBaselines = accounts.keys.count(id => baselines.contains(id)),
      accountsWithPatterns = accounts.keys.count(id => patterns.contains(id)),
      accountsWithAutomation = automationSchedules.size,
      totalExecutionLogs = executionLogs.values.map(_.size).sum,
      activeScheduledRestores = scheduledRestores.size,
      activeCategoryRestrictions = categoryRestrictions.size
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

    // Clean old execution logs
    executionLogs.foreach { case (accountId, logList) =>
      val filtered = logList.filter(_.executedAt.isAfter(cutoff))
      if (filtered.isEmpty) executionLogs.remove(accountId)
      else executionLogs.put(accountId, filtered)
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
  accountsWithPatterns: Int,
  accountsWithAutomation: Int,
  totalExecutionLogs: Int,
  activeScheduledRestores: Int,
  activeCategoryRestrictions: Int
) {
  override def toString: String = {
    s"""Repository Statistics:
       |  Accounts: $totalAccounts
       |  Alerts: $totalAlerts
       |  AI/ML Data:
       |    - Baselines: $totalBaselines (${accountsWithBaselines} accounts)
       |    - Patterns: $totalPatterns (${accountsWithPatterns} accounts)
       |  Automation Data:
       |    - Automation Schedules: $accountsWithAutomation accounts
       |    - Execution Logs: $totalExecutionLogs
       |    - Active Scheduled Restores: $activeScheduledRestores
       |    - Active Category Restrictions: $activeCategoryRestrictions
       |""".stripMargin
  }
}
