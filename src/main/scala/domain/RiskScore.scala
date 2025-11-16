package domain

import java.time.Instant

/** Risk scoring levels */
enum RiskLevel:
  case Low, Medium, High, Critical

/** Represents a risk assessment for an account or transaction */
case class RiskScore(
  level: RiskLevel,
  score: Double, // 0.0 to 1.0
  factors: List[RiskFactor],
  timestamp: Instant = Instant.now(),
  recommendation: String
)

/** Individual risk factors contributing to overall score */
case class RiskFactor(
  name: String,
  weight: Double, // contribution to overall score
  detected: Boolean,
  description: String
)

/** Behavioral pattern detection results */
case class BehaviorPattern(
  patternType: PatternType,
  severity: Double, // 0.0 to 1.0
  occurrences: Int,
  firstDetected: Instant,
  lastDetected: Instant,
  metadata: Map[String, String] = Map.empty
)

enum PatternType:
  case SuddenSpendingIncrease
  case UnusualTimeActivity
  case RepeatedFailedAuth
  case RapidPayeeAdditions
  case HighRiskMerchantFrequency
  case GeographicAnomaly
  case VelocityAnomaly
  case SocialEngineeringIndicators
  case AccountTakeover
  case UnusualCategoryShift

/** Historical behavior baseline for anomaly detection */
case class BehaviorBaseline(
  accountId: String,
  avgDailyTransactions: Double,
  avgTransactionAmount: BigDecimal,
  commonCategories: Map[Category, Int],
  commonTimeRanges: List[TimeRange],
  typicalPayees: Set[String],
  lastUpdated: Instant = Instant.now()
)

case class TimeRange(startHour: Int, endHour: Int)
