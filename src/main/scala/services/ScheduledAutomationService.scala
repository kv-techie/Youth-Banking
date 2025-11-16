package services

import domain._
import java.time.{Instant, ZonedDateTime, ZoneId, Duration}

/** Automated scheduling system for smart modes and dynamic limit adjustments
  * 
  * Features:
  * - Time-based mode switching (e.g., school hours, weekends)
  * - Pattern-based automatic adjustments
  * - Activity-based triggers
  * - Risk-based automation
  */
class ScheduledAutomationService(
  smartModes: SmartModesService,
  monitor: MonitoringService,
  aiEngine: AIRiskEngine
) {

  /** Execute all scheduled automations for an account */
  def executeSchedules(accountId: String): Unit = {
    Repository.getAutomationSchedule(accountId).foreach { schedule =>
      if (schedule.enabled) {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        
        Repository.getAccount(accountId).foreach { account =>
          // Get current risk level and patterns
          val riskScore = aiEngine.analyzeAccountBehavior(accountId)
          val patterns = Repository.getPatterns(accountId, Duration.ofHours(24))
          
          // Sort by priority (higher first), then execute
          val sortedRules = schedule.schedules
            .filter(_.enabled)
            .sortBy(-_.priority)
          
          sortedRules.foreach { rule =>
            if (rule.trigger.shouldTrigger(account, now, patterns, riskScore.level)) {
              executeAction(accountId, rule)
            }
          }
        }
        
        // Update last executed time
        Repository.saveAutomationSchedule(schedule.copy(lastExecuted = Some(Instant.now())))
      }
    }
  }

  /** Execute a specific action */
  private def executeAction(accountId: String, rule: ScheduleRule): Unit = {
    try {
      rule.action match {
        case ApplyModeAction(mode) =>
          smartModes.applyMode(accountId, mode)
          logExecution(rule.id, accountId, rule.trigger.toString, s"Applied mode: $mode", success = true)
          
        case AdjustLimitsAction(monthlyMult, perTxMult, withdrawalMult, resetAfter) =>
          adjustLimits(accountId, monthlyMult, perTxMult, withdrawalMult)
          resetAfter.foreach { hours =>
            scheduleRestore(accountId, hours)
          }
          logExecution(rule.id, accountId, rule.trigger.toString, "Adjusted limits", success = true)
          
        case SendAlertAction(message, alertType, requiresAction) =>
          val alert = Alert(accountId, alertType, message, requiresAction = requiresAction)
          Repository.saveAlert(accountId, alert)
          logExecution(rule.id, accountId, rule.trigger.toString, s"Sent alert: $message", success = true)
          
        case BlockTransactionsAction(durationHours, reason) =>
          blockTransactions(accountId, durationHours, reason)
          logExecution(rule.id, accountId, rule.trigger.toString, s"Blocked transactions: $reason", success = true)
          
        case RestrictToCategoryAction(categories, durationHours) =>
          restrictToCategories(accountId, categories, durationHours)
          logExecution(rule.id, accountId, rule.trigger.toString, s"Restricted to categories: ${categories.mkString(", ")}", success = true)
          
        case TriggerCoolingOffAction(severity, reason) =>
          // This would call CoolingOffService if available
          monitor.makeAlert(accountId, AlertType.GenericNotice, s"Cooling-off triggered: $severity - $reason")
          logExecution(rule.id, accountId, rule.trigger.toString, s"Triggered cooling-off: $severity", success = true)
      }
      
      monitor.makeAlert(accountId, AlertType.GenericNotice, s"Automated rule '${rule.name}' executed")
      
    } catch {
      case e: Exception =>
        logExecution(rule.id, accountId, rule.trigger.toString, s"Failed: ${e.getMessage}", success = false)
        monitor.makeAlert(accountId, AlertType.GenericNotice, s"Failed to execute rule '${rule.name}': ${e.getMessage}")
    }
  }

  /** Adjust account limits */
  private def adjustLimits(
    accountId: String, 
    monthlyMult: Option[Double], 
    perTxMult: Option[Double], 
    withdrawalMult: Option[Double]
  ): Unit = {
    Repository.getAccount(accountId).foreach { acc =>
      val currentLimits = acc.limits
      val newLimits = currentLimits.copy(
        monthly = currentLimits.monthly.map(m => m * BigDecimal(monthlyMult.getOrElse(1.0))),
        perTransaction = currentLimits.perTransaction.map(p => p * BigDecimal(perTxMult.getOrElse(1.0))),
        withdrawalLimits = WithdrawalLimits(
          daily = currentLimits.withdrawalLimits.daily.map(d => d * BigDecimal(withdrawalMult.getOrElse(1.0))),
          weekly = currentLimits.withdrawalLimits.weekly.map(w => w * BigDecimal(withdrawalMult.getOrElse(1.0))),
          monthly = currentLimits.withdrawalLimits.monthly.map(m => m * BigDecimal(withdrawalMult.getOrElse(1.0)))
        )
      )
      
      // Save original limits for restoration
      Repository.saveOriginalLimits(accountId, currentLimits)
      Repository.saveAccount(acc.copy(limits = newLimits))
    }
  }

  /** Schedule restoration of limits */
  private def scheduleRestore(accountId: String, afterHours: Int): Unit = {
    val restoreAt = Instant.now().plusSeconds(afterHours * 3600L)
    Repository.saveScheduledRestore(accountId, restoreAt)
  }

  /** Block all transactions for duration */
  private def blockTransactions(accountId: String, durationHours: Int, reason: String): Unit = {
    Repository.getAccount(accountId).foreach { acc =>
      // Set all limits to zero
      val blockedLimits = acc.limits.copy(
        monthly = Some(BigDecimal(0)),
        perTransaction = Some(BigDecimal(0)),
        withdrawalLimits = WithdrawalLimits(
          daily = Some(BigDecimal(0)),
          weekly = Some(BigDecimal(0)),
          monthly = Some(BigDecimal(0))
        )
      )
      
      Repository.saveOriginalLimits(accountId, acc.limits)
      Repository.saveAccount(acc.copy(limits = blockedLimits))
      scheduleRestore(accountId, durationHours)
      
      monitor.makeAlert(accountId, AlertType.GenericNotice, 
        s"All transactions blocked for $durationHours hours: $reason")
    }
  }

  /** Restrict to specific categories */
  private def restrictToCategories(accountId: String, allowedCategories: Set[Category], durationHours: Int): Unit = {
    Repository.getAccount(accountId).foreach { acc =>
      // Save restriction
      Repository.saveCategoryRestriction(accountId, allowedCategories, Instant.now().plusSeconds(durationHours * 3600L))
      
      monitor.makeAlert(accountId, AlertType.GenericNotice, 
        s"Spending restricted to: ${allowedCategories.mkString(", ")} for $durationHours hours")
    }
  }

  /** Process scheduled restorations */
  def processScheduledRestorations(): Unit = {
    val now = Instant.now()
    Repository.getAllScheduledRestores().foreach { case (accountId, restoreAt) =>
      if (now.isAfter(restoreAt)) {
        Repository.getOriginalLimits(accountId).foreach { originalLimits =>
          Repository.getAccount(accountId).foreach { acc =>
            Repository.saveAccount(acc.copy(limits = originalLimits))
            Repository.clearOriginalLimits(accountId)
            Repository.clearScheduledRestore(accountId)
            monitor.makeAlert(accountId, AlertType.GenericNotice, "Limits automatically restored")
          }
        }
      }
    }
    
    // Clear expired category restrictions
    Repository.getAllCategoryRestrictions().foreach { case (accountId, (categories, expiresAt)) =>
      if (now.isAfter(expiresAt)) {
        Repository.clearCategoryRestriction(accountId)
        monitor.makeAlert(accountId, AlertType.GenericNotice, "Category restrictions lifted")
      }
    }
  }

  /** Log execution */
  private def logExecution(ruleId: String, accountId: String, trigger: String, action: String, success: Boolean): Unit = {
    val log = ScheduleExecutionLog(
      scheduleRuleId = ruleId,
      accountId = accountId,
      executedAt = Instant.now(),
      trigger = trigger,
      action = action,
      success = success,
      message = if (success) "Success" else "Failed"
    )
    Repository.saveExecutionLog(log)
  }
}