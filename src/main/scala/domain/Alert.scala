package domain

import java.time.Instant

/** Alert types that can be raised by the system */
enum AlertType:
  // Original alert types
  case LargeTransaction
  case UnusualMerchant
  case NewPayeeAdded
  case RepeatedPaymentsToUnknownPayee
  case IncomingCreditExceeded
  case NightTimeActivity
  case WithdrawalExceeded
  case RequiresParentApproval
  case PurposeUnlockRequested
  case FraudSuspected
  case GenericNotice
  
  // New AI-driven alert types
  case BehavioralAnomaly
  case HighRiskScore
  case SocialEngineeringDetected
  case AccountTakeoverSuspected
  case VelocityAnomalyDetected
  case PatternDetected
  case BaselineDeviation
  
  // FIXED: Added missing types used in services
  case TransactionBlocked
  case LimitExceeded
  case UnknownPayeeTransaction
  case HighRiskTransaction
  case NightTransaction
  case PurposeFundsUsed
  case WithdrawalLimitExceeded
  case EmergencyOverrideUsed
  case PayeeApprovalRequired
  case CategoryLimitReached
  case MonthlyLimitWarning
  case SuspiciousActivity
  case FraudDetected
  case AccountFrozen
  case CoolingOffActivated
  case ParentActionRequired

/** Alert raised by the system for parent notification or logging
  *
  * @param accountId The account this alert is for
  * @param alertType Type of alert
  * @param message Human-readable message
  * @param timestamp When the alert was created
  * @param riskScore Optional risk score if this alert is AI-generated
  * @param requiresAction Whether this alert requires immediate parent action
  */
case class Alert(
  accountId: String,
  alertType: AlertType,
  message: String,
  timestamp: Instant = Instant.now(),
  riskScore: Option[RiskScore] = None,
  requiresAction: Boolean = false
) {
  /** Check if this is a high-priority alert */
  def isHighPriority: Boolean = alertType match
    case AlertType.FraudSuspected => true
    case AlertType.HighRiskScore => true
    case AlertType.SocialEngineeringDetected => true
    case AlertType.AccountTakeoverSuspected => true
    case AlertType.RequiresParentApproval => true
    case AlertType.TransactionBlocked => true
    case AlertType.HighRiskTransaction => true
    case AlertType.VelocityAnomalyDetected => true
    case AlertType.IncomingCreditExceeded => true
    case AlertType.WithdrawalLimitExceeded => true
    case AlertType.FraudDetected => true
    case AlertType.AccountFrozen => true
    case AlertType.CoolingOffActivated => true
    case AlertType.ParentActionRequired => true
    case _ => false

  /** Check if this is an AI-generated alert */
  def isAIGenerated: Boolean = riskScore.isDefined

  /** Get severity level based on alert type and risk score */
  def severity: AlertSeverity = {
    riskScore match
      case Some(score) => score.level match
        case RiskLevel.Critical => AlertSeverity.Critical
        case RiskLevel.High => AlertSeverity.High
        case RiskLevel.Medium => AlertSeverity.Medium
        case RiskLevel.Low => AlertSeverity.Low
      case None => alertType match
        case AlertType.FraudSuspected => AlertSeverity.Critical
        case AlertType.AccountTakeoverSuspected => AlertSeverity.Critical
        case AlertType.SocialEngineeringDetected => AlertSeverity.High
        case AlertType.HighRiskScore => AlertSeverity.High
        case AlertType.RequiresParentApproval => AlertSeverity.High
        case AlertType.WithdrawalExceeded => AlertSeverity.Medium
        case AlertType.IncomingCreditExceeded => AlertSeverity.Medium
        case AlertType.BehavioralAnomaly => AlertSeverity.Medium
        case AlertType.VelocityAnomalyDetected => AlertSeverity.Medium
        case AlertType.TransactionBlocked => AlertSeverity.High
        case AlertType.FraudDetected => AlertSeverity.Critical
        case AlertType.AccountFrozen => AlertSeverity.Critical
        case AlertType.CoolingOffActivated => AlertSeverity.Medium
        case AlertType.HighRiskTransaction => AlertSeverity.High
        case AlertType.EmergencyOverrideUsed => AlertSeverity.High
        case AlertType.LimitExceeded => AlertSeverity.Medium
        case AlertType.CategoryLimitReached => AlertSeverity.Low
        case AlertType.MonthlyLimitWarning => AlertSeverity.Low
        case _ => AlertSeverity.Low
  }

  /** Get a formatted display string for the alert */
  def display: String = {
    val priorityMarker = if (isHighPriority) "🚨 " else ""
    val aiMarker = if (isAIGenerated) "[AI] " else ""
    val severityLabel = severity match
      case AlertSeverity.Critical => "[CRITICAL]"
      case AlertSeverity.High => "[HIGH]"
      case AlertSeverity.Medium => "[MEDIUM]"
      case AlertSeverity.Low => "[INFO]"
    
    s"$priorityMarker$aiMarker$severityLabel $message"
  }
}

/** Alert severity levels */
enum AlertSeverity:
  case Low, Medium, High, Critical
