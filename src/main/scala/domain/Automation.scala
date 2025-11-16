package domain

import java.time.{Instant, LocalTime, DayOfWeek, ZonedDateTime}

/** Schedule configuration for an account */
case class AutomationSchedule(
  accountId: String,
  schedules: List[ScheduleRule],
  enabled: Boolean = true,
  lastExecuted: Option[Instant] = None
)

/** Individual schedule rule */
case class ScheduleRule(
  id: String = java.util.UUID.randomUUID().toString,
  name: String,
  trigger: ScheduleTrigger,
  action: ScheduleAction,
  priority: Int = 0, // Higher priority rules execute first
  enabled: Boolean = true
)

/** Schedule triggers - when to activate */
sealed trait ScheduleTrigger {
  def shouldTrigger(account: Account, now: ZonedDateTime, patterns: List[BehaviorPattern], riskLevel: RiskLevel): Boolean
}

/** Time-based trigger (specific days and hours) */
case class TimeBasedTrigger(
  daysOfWeek: Set[DayOfWeek],
  startTime: LocalTime,
  endTime: LocalTime
) extends ScheduleTrigger {
  override def shouldTrigger(account: Account, now: ZonedDateTime, patterns: List[BehaviorPattern], riskLevel: RiskLevel): Boolean = {
    val currentDay = now.getDayOfWeek
    val currentTime = now.toLocalTime
    daysOfWeek.contains(currentDay) && 
      !currentTime.isBefore(startTime) && 
      currentTime.isBefore(endTime)
  }
}

/** Pattern-based trigger (suspicious patterns detected) */
case class PatternBasedTrigger(
  patternType: PatternType,
  minOccurrences: Int,
  withinHours: Int
) extends ScheduleTrigger {
  override def shouldTrigger(account: Account, now: ZonedDateTime, patterns: List[BehaviorPattern], riskLevel: RiskLevel): Boolean = {
    patterns.count(_.patternType == patternType) >= minOccurrences
  }
}

/** Risk-based trigger (account risk level threshold) */
case class RiskBasedTrigger(
  minRiskLevel: RiskLevel
) extends ScheduleTrigger {
  override def shouldTrigger(account: Account, now: ZonedDateTime, patterns: List[BehaviorPattern], riskLevel: RiskLevel): Boolean = {
    riskLevel match {
      case RiskLevel.Critical => true
      case RiskLevel.High => minRiskLevel != RiskLevel.Critical
      case RiskLevel.Medium => minRiskLevel == RiskLevel.Medium || minRiskLevel == RiskLevel.Low
      case RiskLevel.Low => minRiskLevel == RiskLevel.Low
    }
  }
}

/** Activity-based trigger (transaction velocity) */
case class ActivityBasedTrigger(
  minTransactions: Int,
  withinHours: Int
) extends ScheduleTrigger {
  override def shouldTrigger(account: Account, now: ZonedDateTime, patterns: List[BehaviorPattern], riskLevel: RiskLevel): Boolean = {
    val cutoff = now.toInstant.minusSeconds(withinHours * 3600L)
    account.transactions.count(_.timestamp.isAfter(cutoff)) >= minTransactions
  }
}

/** Balance-based trigger */
case class BalanceBasedTrigger(
  minBalance: BigDecimal,
  maxBalance: BigDecimal
) extends ScheduleTrigger {
  override def shouldTrigger(account: Account, now: ZonedDateTime, patterns: List[BehaviorPattern], riskLevel: RiskLevel): Boolean = {
    account.totalBalance >= minBalance && account.totalBalance <= maxBalance
  }
}

/** Composite trigger (AND/OR logic) */
case class CompositeTrigger(
  triggers: List[ScheduleTrigger],
  logic: TriggerLogic
) extends ScheduleTrigger {
  override def shouldTrigger(account: Account, now: ZonedDateTime, patterns: List[BehaviorPattern], riskLevel: RiskLevel): Boolean = {
    logic match {
      case TriggerLogic.AND => triggers.forall(_.shouldTrigger(account, now, patterns, riskLevel))
      case TriggerLogic.OR => triggers.exists(_.shouldTrigger(account, now, patterns, riskLevel))
    }
  }
}

enum TriggerLogic:
  case AND, OR

/** Schedule actions - what to do when triggered */
sealed trait ScheduleAction

/** Apply a safety mode */
case class ApplyModeAction(mode: SafetyMode) extends ScheduleAction

/** Adjust limits by multipliers */
case class AdjustLimitsAction(
  monthlyMultiplier: Option[Double] = None,
  perTransactionMultiplier: Option[Double] = None,
  withdrawalMultiplier: Option[Double] = None,
  resetAfterHours: Option[Int] = None // Auto-restore after N hours
) extends ScheduleAction

/** Send alert to parent */
case class SendAlertAction(
  message: String,
  alertType: AlertType,
  requiresAction: Boolean = false
) extends ScheduleAction

/** Block all transactions */
case class BlockTransactionsAction(
  durationHours: Int,
  reason: String
) extends ScheduleAction

/** Allow specific category only */
case class RestrictToCategoryAction(
  allowedCategories: Set[Category],
  durationHours: Int
) extends ScheduleAction

/** Trigger cooling-off */
case class TriggerCoolingOffAction(
  severity: String, // "Mild", "Moderate", "Severe", "Critical"
  reason: String
) extends ScheduleAction

/** Execution log entry */
case class ScheduleExecutionLog(
  scheduleRuleId: String,
  accountId: String,
  executedAt: Instant,
  trigger: String,
  action: String,
  success: Boolean,
  message: String
)
