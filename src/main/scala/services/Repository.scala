package services

import domain._
import scala.collection.mutable
import java.time.{Duration, Instant}

/** In-memory storage for demo purposes.
  * In production, replace with a real database.
  */
object Repository {
  // Core storage
  private val accounts = mutable.Map.empty[String, Account]
  private val alerts = mutable.Map.empty[String, List[Alert]]
  
  // AI/ML storage
  private val baselines = mutable.Map.empty[String, BehaviorBaseline]
  private val patterns = mutable.Map.empty[String, List[BehaviorPattern]]
  
  // Automation storage
  private val automationSchedules = mutable.Map.empty[String, AutomationSchedule]
  private val originalLimits = mutable.Map.empty[String, Limits]
  private val scheduledRestores = mutable.Map.empty[String, Instant]
  private val categoryRestrictions = mutable.Map.empty[String, (Set[Category], Instant)]
  private val executionLogs = mutable.Map.empty[String, List[ScheduleExecutionLog]]
  
  // Fraud detection storage
  private val fraudHistories = mutable.Map.empty[String, FraudScoreHistory]
  
  // Cooling-off storage
  private val coolingOffStates = mutable.Map.empty[String, CoolingOffState]
  private val coolingOffConfigs = mutable.Map.empty[String, CoolingOffConfig]
  private val coolingOffHistories = mutable.Map.empty[String, List[CoolingOffHistoryEntry]]
  private val coolingOffTriggers = mutable.Map.empty[String, List[CoolingOffTrigger]]
  
  // Parent dashboard storage
  private val pendingApprovals = mutable.Map.empty[String, List[PendingApproval]]
  private val parentActions = mutable.Map.empty[String, List[ParentAction]]
  private val parentNotifications = mutable.Map.empty[String, List[ParentNotification]]
  private val parentPreferences = mutable.Map.empty[String, ParentPreferences]
  private val readAlerts = mutable.Map.empty[(String, String, Instant), Boolean] // (parentId, accountId, alertTimestamp)
  private val accountParentMapping = mutable.Map.empty[String, String] // accountId -> parentId

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
          severity = (p.severity + pattern.severity) / 2,
          occurrences = p.occurrences + pattern.occurrences,
          lastDetected = pattern.lastDetected,
          metadata = p.metadata ++ pattern.metadata
        )
        patterns.put(accountId, updated :: current.filterNot(_ == p))
      case None =>
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

  // ===== Original Limits Operations =====
  
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

  // ===== Fraud History Operations =====
  
  def getFraudHistory(accountId: String): Option[FraudScoreHistory] = 
    fraudHistories.get(accountId)
  
  def saveFraudHistory(history: FraudScoreHistory): Unit = 
    fraudHistories.put(history.accountId, history)
  
  def clearFraudHistory(accountId: String): Unit = 
    fraudHistories.remove(accountId)

  def getAllFraudHistories: List[FraudScoreHistory] = 
    fraudHistories.values.toList

  // ===== Cooling-Off State Operations =====
  
  def getCoolingOffState(accountId: String): Option[CoolingOffState] = 
    coolingOffStates.get(accountId)
  
  def saveCoolingOffState(state: CoolingOffState): Unit = 
    coolingOffStates.put(state.accountId, state)
  
  def deleteCoolingOffState(accountId: String): Unit = 
    coolingOffStates.remove(accountId)

  def getAllActiveCoolingOffs: List[CoolingOffState] = 
    coolingOffStates.values.filter(_.isActive).toList

  // ===== Cooling-Off Config Operations =====
  
  def getCoolingOffConfig(accountId: String): Option[CoolingOffConfig] = 
    coolingOffConfigs.get(accountId)
  
  def saveCoolingOffConfig(config: CoolingOffConfig): Unit = 
    coolingOffConfigs.put(config.accountId, config)

  // ===== Cooling-Off History Operations =====
  
  def getCoolingOffHistory(accountId: String): List[CoolingOffHistoryEntry] = 
    coolingOffHistories.getOrElse(accountId, Nil)
  
  def saveCoolingOffHistory(accountId: String, entry: CoolingOffHistoryEntry): Unit = {
    val current = coolingOffHistories.getOrElse(accountId, Nil)
    coolingOffHistories.put(accountId, entry :: current)
  }

  def clearCoolingOffHistory(accountId: String): Unit = 
    coolingOffHistories.remove(accountId)

  // ===== Cooling-Off Trigger Operations =====
  
  def saveCoolingOffTrigger(accountId: String, trigger: CoolingOffTrigger): Unit = {
    val current = coolingOffTriggers.getOrElse(accountId, Nil)
    coolingOffTriggers.put(accountId, trigger :: current)
  }
  
  def getCoolingOffTriggers(accountId: String): List[CoolingOffTrigger] = 
    coolingOffTriggers.getOrElse(accountId, Nil)

  // ===== Pending Approval Operations =====
  
  def getPendingApprovals(accountId: String): List[PendingApproval] = 
    pendingApprovals.getOrElse(accountId, Nil).filter(_.status == ApprovalStatus.Pending)
  
  def getAllPendingApprovals: List[PendingApproval] =
    pendingApprovals.values.flatten.filter(_.status == ApprovalStatus.Pending).toList
  
  def getPendingApprovalById(approvalId: String): Option[PendingApproval] = 
    pendingApprovals.values.flatten.find(_.approvalId == approvalId)
  
  def savePendingApproval(approval: PendingApproval): Unit = {
    val current = pendingApprovals.getOrElse(approval.accountId, Nil)
    pendingApprovals.put(approval.accountId, approval :: current)
  }
  
  def updateApprovalStatus(approvalId: String, status: ApprovalStatus): Unit = {
    pendingApprovals.foreach { case (accountId, approvals) =>
      val updated = approvals.map { approval =>
        if (approval.approvalId == approvalId) approval.copy(status = status)
        else approval
      }
      pendingApprovals.put(accountId, updated)
    }
  }

  def removePendingApproval(approvalId: String): Unit = {
    pendingApprovals.foreach { case (accountId, approvals) =>
      val filtered = approvals.filterNot(_.approvalId == approvalId)
      if (filtered.isEmpty) pendingApprovals.remove(accountId)
      else pendingApprovals.put(accountId, filtered)
    }
  }

  // ===== Parent Action Operations =====
  
  def saveParentAction(action: ParentAction): Unit = {
    val current = parentActions.getOrElse(action.parentId, Nil)
    parentActions.put(action.parentId, action :: current)
  }
  
  def getParentActions(parentId: String, accountId: Option[String]): List[ParentAction] = {
    val actions = parentActions.getOrElse(parentId, Nil)
    accountId match {
      case Some(accId) => actions.filter(_.accountId == accId)
      case None => actions
    }
  }

  def clearParentActions(parentId: String): Unit = 
    parentActions.remove(parentId)

  // ===== Parent Notification Operations =====
  
  def saveParentNotification(notification: ParentNotification): Unit = {
    val current = parentNotifications.getOrElse(notification.parentId, Nil)
    parentNotifications.put(notification.parentId, notification :: current)
  }
  
  def getParentNotifications(parentId: String): List[ParentNotification] = 
    parentNotifications.getOrElse(parentId, Nil)
  
  def markNotificationRead(notificationId: String): Unit = {
    parentNotifications.foreach { case (parentId, notifications) =>
      val updated = notifications.map { notif =>
        if (notif.notificationId == notificationId) notif.copy(read = true)
        else notif
      }
      parentNotifications.put(parentId, updated)
    }
  }

  def clearNotifications(parentId: String): Unit = 
    parentNotifications.remove(parentId)

  // ===== Parent Preferences Operations =====
  
  def getParentPreferences(parentId: String): Option[ParentPreferences] = 
    parentPreferences.get(parentId)
  
  def saveParentPreferences(prefs: ParentPreferences): Unit = 
    parentPreferences.put(prefs.parentId, prefs)

  // ===== Alert Read Status Operations =====
  
  def markAlertRead(parentId: String, accountId: String, alertTimestamp: Instant): Unit = 
    readAlerts.put((parentId, accountId, alertTimestamp), true)
  
  def isAlertRead(parentId: String, accountId: String, alert: Alert): Boolean = 
    readAlerts.getOrElse((parentId, accountId, alert.timestamp), false)

  def clearReadAlerts(parentId: String, accountId: String): Unit = {
    readAlerts.filterInPlace { case ((pId, accId, _), _) =>
      !(pId == parentId && accId == accountId)
    }
  }

  // ===== Account-Parent Mapping Operations =====
  
  def linkAccountToParent(accountId: String, parentId: String): Unit = 
    accountParentMapping.put(accountId, parentId)
  
  def unlinkAccount(accountId: String): Unit = 
    accountParentMapping.remove(accountId)
  
  def getAccountsByParent(parentId: String): List[Account] = 
    accountParentMapping.filter(_._2 == parentId).keys.flatMap(getAccount).toList
  
  def getParentForAccount(accountId: String): Option[String] = 
    accountParentMapping.get(accountId)

  // ===== Minor Operations (placeholder) =====
  
  private val minors = mutable.Map.empty[String, Minor]
  
  def getMinor(minorId: String): Option[Minor] = 
    minors.get(minorId).orElse(Some(Minor(id = minorId, name = s"Minor-$minorId", age = 16)))
  
  def saveMinor(minor: Minor): Unit = 
    minors.put(minor.id, minor)

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
    fraudHistories.clear()
    coolingOffStates.clear()
    coolingOffConfigs.clear()
    coolingOffHistories.clear()
    coolingOffTriggers.clear()
    pendingApprovals.clear()
    parentActions.clear()
    parentNotifications.clear()
    parentPreferences.clear()
    readAlerts.clear()
    accountParentMapping.clear()
    minors.clear()
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

  /** Clear only fraud detection data */
  def clearFraudData(): Unit = {
    fraudHistories.clear()
  }

  /** Clear only cooling-off data */
  def clearCoolingOffData(): Unit = {
    coolingOffStates.clear()
    coolingOffHistories.clear()
    coolingOffTriggers.clear()
  }

  /** Clear only parent dashboard data */
  def clearParentDashboardData(): Unit = {
    pendingApprovals.clear()
    parentActions.clear()
    parentNotifications.clear()
    readAlerts.clear()
  }

  /** Get comprehensive statistics */
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
      activeCategoryRestrictions = categoryRestrictions.size,
      accountsWithFraudHistory = fraudHistories.size,
      totalFraudDetections = fraudHistories.values.map(_.totalFraudDetected).sum,
      activeCoolingOffs = coolingOffStates.values.count(_.isActive),
      totalCoolingOffEvents = coolingOffHistories.values.map(_.size).sum,
      totalPendingApprovals = pendingApprovals.values.flatten.count(_.status == ApprovalStatus.Pending),
      totalParentActions = parentActions.values.map(_.size).sum,
      totalNotifications = parentNotifications.values.map(_.size).sum,
      unreadNotifications = parentNotifications.values.flatten.count(!_.read)
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

    // Clean old fraud history entries
    fraudHistories.foreach { case (accountId, history) =>
      val filtered = history.scores.filter(_._1.isAfter(cutoff))
      if (filtered.isEmpty) {
        fraudHistories.remove(accountId)
      } else {
        fraudHistories.put(accountId, history.copy(scores = filtered))
      }
    }

    // Clean old cooling-off history
    coolingOffHistories.foreach { case (accountId, historyList) =>
      val filtered = historyList.filter(_.triggeredAt.isAfter(cutoff))
      if (filtered.isEmpty) coolingOffHistories.remove(accountId)
      else coolingOffHistories.put(accountId, filtered)
    }

    // Clean old cooling-off triggers
    coolingOffTriggers.foreach { case (accountId, triggerList) =>
      val filtered = triggerList.filter(_.triggeredAt.isAfter(cutoff))
      if (filtered.isEmpty) coolingOffTriggers.remove(accountId)
      else coolingOffTriggers.put(accountId, filtered)
    }

    // Clean old pending approvals (expired or completed)
    pendingApprovals.foreach { case (accountId, approvalList) =>
      val filtered = approvalList.filter(a => 
        a.submittedAt.isAfter(cutoff) || a.status == ApprovalStatus.Pending
      )
      if (filtered.isEmpty) pendingApprovals.remove(accountId)
      else pendingApprovals.put(accountId, filtered)
    }

    // Clean old parent actions
    parentActions.foreach { case (parentId, actionList) =>
      val filtered = actionList.filter(_.timestamp.isAfter(cutoff))
      if (filtered.isEmpty) parentActions.remove(parentId)
      else parentActions.put(parentId, filtered)
    }

    // Clean old notifications
    parentNotifications.foreach { case (parentId, notifList) =>
      val filtered = notifList.filter(_.createdAt.isAfter(cutoff))
      if (filtered.isEmpty) parentNotifications.remove(parentId)
      else parentNotifications.put(parentId, filtered)
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
  activeCategoryRestrictions: Int,
  accountsWithFraudHistory: Int,
  totalFraudDetections: Int,
  activeCoolingOffs: Int,
  totalCoolingOffEvents: Int,
  totalPendingApprovals: Int,
  totalParentActions: Int,
  totalNotifications: Int,
  unreadNotifications: Int
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
       |  Fraud Detection:
       |    - Fraud Histories: $accountsWithFraudHistory accounts
       |    - Total Fraud Detections: $totalFraudDetections
       |  Cooling-Off System:
       |    - Active Cooling-Offs: $activeCoolingOffs
       |    - Total Historical Events: $totalCoolingOffEvents
       |  Parent Dashboard:
       |    - Pending Approvals: $totalPendingApprovals
       |    - Parent Actions: $totalParentActions
       |    - Notifications: $totalNotifications ($unreadNotifications unread)
       |""".stripMargin
  }
}

/** Placeholder Minor model */
case class Minor(id: String, name: String, age: Int)
