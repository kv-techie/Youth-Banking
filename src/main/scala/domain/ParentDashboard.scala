package domain

import java.time.Instant

/** Parent dashboard summary */
case class DashboardSummary(
  parentId: String,
  linkedAccounts: List[AccountSummary],
  pendingApprovals: Int,
  highPriorityAlerts: Int,
  activePatterns: Int,
  overallRiskLevel: RiskLevel,
  lastRefreshed: Instant = Instant.now()
)

/** Account summary for dashboard */
case class AccountSummary(
  accountId: String,
  minorId: String,
  minorName: String,
  balance: BigDecimal,
  availableBalance: BigDecimal,
  pendingApprovals: Int,
  recentAlerts: Int,
  unreadAlerts: Int,
  riskLevel: RiskLevel,
  coolingOffActive: Boolean,
  lastActivity: Option[Instant]
)

/** Pending approval item */
case class PendingApproval(
  approvalId: String = java.util.UUID.randomUUID().toString,
  accountId: String,
  transaction: Transaction,
  riskScore: Option[RiskScore] = None,
  fraudScore: Option[FraudDetectionResult] = None,
  submittedAt: Instant = Instant.now(),
  reason: String,
  status: ApprovalStatus = ApprovalStatus.Pending
)

enum ApprovalStatus:
  case Pending, Approved, Rejected, Expired

/** Parent action log */
case class ParentAction(
  actionId: String = java.util.UUID.randomUUID().toString,
  parentId: String,
  accountId: String,
  actionType: ParentActionType,
  description: String,
  timestamp: Instant = Instant.now(),
  metadata: Map[String, String] = Map.empty
)

enum ParentActionType:
  case ApprovedTransaction
  case RejectedTransaction
  case UpdatedLimits
  case AddedTrustedPayee
  case RemovedPayee
  case OverrideCoolingOff
  case TriggeredCoolingOff
  case EnabledAutomation
  case DisabledAutomation
  case FrozeAccount
  case UnfrozeAccount
  case UpdatedSchedule
  case AcknowledgedAlert

/** Real-time notification for parents */
case class ParentNotification(
  notificationId: String = java.util.UUID.randomUUID().toString,
  parentId: String,
  accountId: String,
  notificationType: NotificationType,
  title: String,
  message: String,
  priority: NotificationPriority,
  createdAt: Instant = Instant.now(),
  read: Boolean = false,
  actionRequired: Boolean = false,
  relatedAlertId: Option[String] = None,
  relatedApprovalId: Option[String] = None
)

enum NotificationType:
  case TransactionAlert
  case ApprovalRequest
  case RiskWarning
  case CoolingOffActivated
  case LimitExceeded
  case FraudDetected
  case SystemNotification
  case AccountActivity

enum NotificationPriority:
  case Low, Medium, High, Urgent

/** Parent preferences */
case class ParentPreferences(
  parentId: String,
  notificationSettings: NotificationSettings = NotificationSettings(),
  autoApprovalRules: List[AutoApprovalRule] = List.empty,
  alertThresholds: Map[AlertType, Boolean] = Map.empty, // Which alerts to receive
  updatedAt: Instant = Instant.now()
)

/** Notification settings */
case class NotificationSettings(
  enablePushNotifications: Boolean = true,
  enableEmailNotifications: Boolean = true,
  enableSMSNotifications: Boolean = false,
  quietHoursStart: Option[java.time.LocalTime] = None,
  quietHoursEnd: Option[java.time.LocalTime] = None,
  minPriorityForNotification: NotificationPriority = NotificationPriority.Medium
)

/** Auto-approval rules */
case class AutoApprovalRule(
  ruleId: String = java.util.UUID.randomUUID().toString,
  name: String,
  enabled: Boolean = true,
  conditions: AutoApprovalConditions,
  maxAmount: BigDecimal,
  allowedCategories: Set[Category] = Set.empty,
  allowedPayees: Set[String] = Set.empty
)

case class AutoApprovalConditions(
  maxRiskScore: Double = 0.3, // Only auto-approve low-risk
  requireTrustedPayee: Boolean = true,
  onlyDuringDayTime: Boolean = true, // 7 AM - 9 PM
  maxDailyAutoApprovals: Int = 3
)

/** Parent session info */
case class ParentSession(
  sessionId: String = java.util.UUID.randomUUID().toString,
  parentId: String,
  linkedAccountIds: List[String],
  startedAt: Instant = Instant.now(),
  lastActivityAt: Instant = Instant.now(),
  deviceInfo: String = "Unknown"
)
