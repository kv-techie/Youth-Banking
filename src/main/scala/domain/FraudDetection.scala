package domain

import java.time.Instant

/** Fraud detection result */
case class FraudDetectionResult(
  isFraud: Boolean,
  confidence: Double, // 0.0 to 1.0
  detectionMethods: List[String],
  evidence: List[FraudEvidence],
  recommendation: FraudRecommendation,
  detectedAt: Instant = Instant.now()
)

/** Individual piece of fraud evidence */
case class FraudEvidence(
  evidenceType: FraudEvidenceType,
  severity: Double, // 0.0 to 1.0
  description: String,
  metadata: Map[String, String] = Map.empty
)

enum FraudEvidenceType:
  case SequentialAnomaly
  case PayeeNetworkRisk
  case SpendingAnomaly
  case BehavioralDeviation
  case VelocityAnomaly
  case GeographicAnomaly
  case TestTransactionPattern
  case RoundTrippingDetected
  case AccountTakeoverIndicator
  case SocialEngineeringPattern
  case NewAccountRisk
  case HighRiskMerchant
  case UnusualTimePattern
  case CategoryShiftAnomaly

/** Fraud recommendation actions */
enum FraudRecommendation:
  case BlockImmediately
  case RequireParentVerification
  case FlagForReview
  case AllowWithMonitoring
  case Proceed

/** Fraud score history for tracking */
case class FraudScoreHistory(
  accountId: String,
  scores: List[(Instant, Double)], // timestamp -> confidence score
  totalFraudDetected: Int,
  totalFalsePositives: Int,
  lastUpdated: Instant = Instant.now()
)

/** Transaction sequence for pattern analysis */
case class TransactionSequence(
  accountId: String,
  transactions: List[Transaction],
  sequenceType: SequenceType,
  detectedAt: Instant,
  riskScore: Double
)

enum SequenceType:
  case EscalatingAmounts
  case RapidFire
  case RoundTripping
  case TestAndExecute
  case CategoryHopping
  case SplitTransaction

/** Payee network graph node */
case class PayeeNode(
  payeeId: String,
  trustScore: Double, // 0.0 to 1.0
  transactionCount: Int,
  totalAmount: BigDecimal,
  firstSeen: Instant,
  lastSeen: Instant,
  connectedPayees: Set[String] = Set.empty,
  riskFlags: List[String] = List.empty
)

/** Statistical metrics for anomaly detection */
case class StatisticalMetrics(
  mean: Double,
  stdDev: Double,
  median: Double,
  q1: Double, // 25th percentile
  q3: Double, // 75th percentile
  iqr: Double, // Interquartile range
  outlierThreshold: Double // IQR-based
)
