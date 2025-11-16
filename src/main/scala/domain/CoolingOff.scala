package domain

import java.time.Instant

/** Cooling-off state for an account */
case class CoolingOffState(
  accountId: String,
  isActive: Boolean,
  severity: CoolingOffSeverity,
  triggeredAt: Instant,
  triggeredBy: String, // reason or rule that triggered it
  originalLimits: Limits,
  cooledLimits: Limits,
  recoveryStartTime: Option[Instant] = None,
  expiresAt: Instant,
  parentCanOverride: Boolean = true
)

/** Cooling-off severity levels */
enum CoolingOffSeverity:
  case Mild, Moderate, Severe, Critical

/** Cooling-off trigger reasons */
case class CoolingOffTrigger(
  reason: String,
  triggerType: CoolingOffTriggerType,
  detectedPatterns: List[PatternType] = List.empty,
  riskScore: Option[Double] = None,
  triggeredAt: Instant = Instant.now()
)

enum CoolingOffTriggerType:
  case AutomaticRiskBased
  case AutomaticPatternBased
  case ManualParentTriggered
  case FraudDetectionTriggered
  case VelocityLimitExceeded
  case SuspiciousActivityDetected

/** Cooling-off configuration */
case class CoolingOffConfig(
  accountId: String,
  enabled: Boolean = true,
  autoTriggerThresholds: AutoTriggerThresholds = AutoTriggerThresholds(),
  recoverySettings: RecoverySettings = RecoverySettings()
)

/** Thresholds for automatic cooling-off activation */
case class AutoTriggerThresholds(
  minRiskScoreForMild: Double = 0.4,
  minRiskScoreForModerate: Double = 0.6,
  minRiskScoreForSevere: Double = 0.75,
  minRiskScoreForCritical: Double = 0.9,
  minPatternsForTrigger: Int = 2,
  minHighSeverityPatterns: Int = 1
)

/** Recovery settings for gradual restoration */
case class RecoverySettings(
  enableGradualRecovery: Boolean = true,
  recoveryStartsAtPercent: Double = 0.5, // Start recovery at 50% of cooling period
  fullRecoveryAtPercent: Double = 1.0
)

/** Cooling-off history entry */
case class CoolingOffHistoryEntry(
  accountId: String,
  triggeredAt: Instant,
  endedAt: Instant,
  severity: CoolingOffSeverity,
  reason: String,
  wasOverriddenByParent: Boolean,
  effectiveDuration: Long // in seconds
)
