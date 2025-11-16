package services

import domain._
import java.time.{Instant, ZonedDateTime, ZoneId, Duration}
import scala.math._

/** AI-driven behavioral analysis and risk scoring engine
  * 
  * This engine analyzes transactions, account behavior, and patterns to:
  * 1. Detect anomalies in spending behavior
  * 2. Identify potential fraud or social engineering attempts
  * 3. Score risk levels for transactions and accounts
  * 4. Learn normal behavior patterns over time
  */
class AIRiskEngine(monitor: MonitoringService) {

  private val HIGH_RISK_THRESHOLD = 0.7
  private val MEDIUM_RISK_THRESHOLD = 0.4
  private val CRITICAL_RISK_THRESHOLD = 0.85

  /** Analyze a transaction before it's processed and return risk score */
  def analyzeTransaction(accountId: String, tx: Transaction): RiskScore = {
    Repository.getAccount(accountId) match
      case None => 
        RiskScore(RiskLevel.Critical, 1.0, List.empty, recommendation = "Account not found")
      case Some(acc) =>
        val baseline = getOrCreateBaseline(acc)
        val patterns = detectPatterns(acc, tx, baseline)
        val factors = evaluateRiskFactors(acc, tx, baseline, patterns)
        val totalScore = calculateWeightedScore(factors)
        val level = scoreToLevel(totalScore)
        
        // Store detected patterns for learning
        patterns.foreach(p => storePattern(accountId, p))
        
        RiskScore(
          level = level,
          score = totalScore,
          factors = factors,
          recommendation = generateRecommendation(level, factors)
        )
  }

  /** Analyze overall account behavior and return risk assessment */
  def analyzeAccountBehavior(accountId: String): RiskScore = {
    Repository.getAccount(accountId) match
      case None => 
        RiskScore(RiskLevel.Critical, 1.0, List.empty, recommendation = "Account not found")
      case Some(acc) =>
        val baseline = getOrCreateBaseline(acc)
        val recentPatterns = getRecentPatterns(accountId, Duration.ofDays(7))
        val factors = evaluateAccountRiskFactors(acc, baseline, recentPatterns)
        val totalScore = calculateWeightedScore(factors)
        val level = scoreToLevel(totalScore)
        
        RiskScore(
          level = level,
          score = totalScore,
          factors = factors,
          recommendation = generateRecommendation(level, factors)
        )
  }

  /** Detect behavioral patterns in transaction */
  private def detectPatterns(acc: Account, tx: Transaction, baseline: BehaviorBaseline): List[BehaviorPattern] = {
    var patterns = List.empty[BehaviorPattern]
    val now = tx.timestamp

    // Pattern 1: Sudden spending increase
    if (tx.amount > baseline.avgTransactionAmount * 3) {
      patterns = BehaviorPattern(
        PatternType.SuddenSpendingIncrease,
        severity = min(1.0, (tx.amount / baseline.avgTransactionAmount).toDouble / 5.0),
        occurrences = 1,
        firstDetected = now,
        lastDetected = now,
        metadata = Map("amount" -> tx.amount.toString, "baseline" -> baseline.avgTransactionAmount.toString)
      ) :: patterns
    }

    // Pattern 2: Unusual time activity
    val hour = ZonedDateTime.ofInstant(now, ZoneId.systemDefault()).getHour
    val isUnusualTime = !baseline.commonTimeRanges.exists(r => hour >= r.startHour && hour < r.endHour)
    if (isUnusualTime && (hour < 6 || hour > 22)) {
      patterns = BehaviorPattern(
        PatternType.UnusualTimeActivity,
        severity = if (hour >= 0 && hour < 5) 0.8 else 0.5,
        occurrences = 1,
        firstDetected = now,
        lastDetected = now,
        metadata = Map("hour" -> hour.toString)
      ) :: patterns
    }

    // Pattern 3: Rapid payee additions (velocity check)
    val recentPayees = acc.payees.filter(p => Duration.between(p.addedAt, now).toHours < 24)
    if (recentPayees.size > 3) {
      patterns = BehaviorPattern(
        PatternType.RapidPayeeAdditions,
        severity = min(1.0, recentPayees.size / 5.0),
        occurrences = recentPayees.size,
        firstDetected = recentPayees.minBy(_.addedAt).addedAt,
        lastDetected = now,
        metadata = Map("count" -> recentPayees.size.toString)
      ) :: patterns
    }

    // Pattern 4: Unusual category shift
    val recentTxns = acc.transactions.filter(t => Duration.between(t.timestamp, now).toDays < 7)
    val currentCategoryFreq = recentTxns.groupBy(_.category).view.mapValues(_.size).toMap
    val baselineTopCategory = baseline.commonCategories.maxByOption(_._2).map(_._1)
    
    baselineTopCategory.foreach { topCat =>
      if (!currentCategoryFreq.contains(topCat) || currentCategoryFreq.getOrElse(topCat, 0) < 2) {
        patterns = BehaviorPattern(
          PatternType.UnusualCategoryShift,
          severity = 0.6,
          occurrences = 1,
          firstDetected = now,
          lastDetected = now,
          metadata = Map("expected" -> topCat.toString, "current" -> tx.category.toString)
        ) :: patterns
      }
    }

    // Pattern 5: High-risk merchant frequency (simulated - would integrate with merchant DB)
    val highRiskCategories = Set(Category.Other) // Placeholder for gambling, crypto, etc.
    if (highRiskCategories.contains(tx.category)) {
      val highRiskCount = recentTxns.count(t => highRiskCategories.contains(t.category))
      if (highRiskCount > 2) {
        patterns = BehaviorPattern(
          PatternType.HighRiskMerchantFrequency,
          severity = min(1.0, highRiskCount / 5.0),
          occurrences = highRiskCount,
          firstDetected = now,
          lastDetected = now,
          metadata = Map("count" -> highRiskCount.toString)
        ) :: patterns
      }
    }

    // Pattern 6: Velocity anomaly - too many transactions in short time
    val last1Hour = acc.transactions.filter(t => Duration.between(t.timestamp, now).toMinutes < 60)
    if (last1Hour.size > 5) {
      patterns = BehaviorPattern(
        PatternType.VelocityAnomaly,
        severity = min(1.0, last1Hour.size / 10.0),
        occurrences = last1Hour.size,
        firstDetected = last1Hour.minBy(_.timestamp).timestamp,
        lastDetected = now,
        metadata = Map("count" -> last1Hour.size.toString, "window" -> "1hour")
      ) :: patterns
    }

    // Pattern 7: Social engineering indicators - new payee + large amount + night time
    tx.toPayeeId.foreach { payeeId =>
      val isNewPayee = !baseline.typicalPayees.contains(payeeId)
      val isLargeAmount = tx.amount > baseline.avgTransactionAmount * 2
      val isNightTime = hour >= 21 || hour < 7
      
      if (isNewPayee && isLargeAmount && isNightTime) {
        patterns = BehaviorPattern(
          PatternType.SocialEngineeringIndicators,
          severity = 0.9,
          occurrences = 1,
          firstDetected = now,
          lastDetected = now,
          metadata = Map("newPayee" -> "true", "nightTime" -> "true", "largeAmount" -> "true")
        ) :: patterns
      }
    }

    patterns
  }

  /** Evaluate all risk factors for a transaction */
  private def evaluateRiskFactors(acc: Account, tx: Transaction, baseline: BehaviorBaseline, patterns: List[BehaviorPattern]): List[RiskFactor] = {
    var factors = List.empty[RiskFactor]

    // Factor 1: Transaction amount anomaly
    val amountRatio = (tx.amount / baseline.avgTransactionAmount).toDouble
    factors = RiskFactor(
      name = "Amount Anomaly",
      weight = if (amountRatio > 5) 0.25 else if (amountRatio > 3) 0.15 else 0.05,
      detected = amountRatio > 2,
      description = f"Transaction ₹${tx.amount} is ${amountRatio}%.1fx the baseline ₹${baseline.avgTransactionAmount}"
    ) :: factors

    // Factor 2: Time-based risk
    val hour = ZonedDateTime.ofInstant(tx.timestamp, ZoneId.systemDefault()).getHour
    val isHighRiskTime = hour >= 22 || hour < 6
    factors = RiskFactor(
      name = "Time Risk",
      weight = if (isHighRiskTime) 0.20 else 0.05,
      detected = isHighRiskTime,
      description = f"Transaction at ${hour}:00 (high-risk: ${isHighRiskTime})"
    ) :: factors

    // Factor 3: New payee risk
    val isNewPayee = tx.toPayeeId.exists(id => !baseline.typicalPayees.contains(id))
    factors = RiskFactor(
      name = "Unknown Payee",
      weight = if (isNewPayee) 0.15 else 0.0,
      detected = isNewPayee,
      description = if (isNewPayee) "Transfer to new/unknown payee" else "Transfer to known payee"
    ) :: factors

    // Factor 4: Transaction velocity
    val recentCount = acc.transactions.filter(t => Duration.between(t.timestamp, tx.timestamp).toHours < 1).size
    val highVelocity = recentCount > 3
    factors = RiskFactor(
      name = "Transaction Velocity",
      weight = if (highVelocity) 0.20 else 0.05,
      detected = highVelocity,
      description = f"${recentCount} transactions in last hour"
    ) :: factors

    // Factor 5: Failed authentication attempts (simulated - would need auth service)
    // Placeholder for integration with authentication system
    factors = RiskFactor(
      name = "Auth Failures",
      weight = 0.0,
      detected = false,
      description = "No recent failed authentication attempts"
    ) :: factors

    // Factor 6: Pattern-based risks
    patterns.foreach { pattern =>
      val patternWeight = pattern.severity * 0.3
      factors = RiskFactor(
        name = s"Pattern: ${pattern.patternType}",
        weight = patternWeight,
        detected = true,
        description = s"Detected ${pattern.patternType} with severity ${(pattern.severity * 100).toInt}%"
      ) :: factors
    }

    // Factor 7: Account age and history
    val accountAge = Duration.between(acc.createdAt, tx.timestamp).toDays
    val newAccountRisk = accountAge < 30
    factors = RiskFactor(
      name = "Account Maturity",
      weight = if (newAccountRisk) 0.10 else 0.0,
      detected = newAccountRisk,
      description = f"Account ${accountAge} days old (new: ${newAccountRisk})"
    ) :: factors

    factors
  }

  /** Evaluate account-level risk factors */
  private def evaluateAccountRiskFactors(acc: Account, baseline: BehaviorBaseline, patterns: List[BehaviorPattern]): List[RiskFactor] = {
    var factors = List.empty[RiskFactor]

    // Factor 1: Overall pattern severity
    val avgPatternSeverity = if (patterns.isEmpty) 0.0 else patterns.map(_.severity).sum / patterns.size
    factors = RiskFactor(
      name = "Behavioral Patterns",
      weight = avgPatternSeverity * 0.4,
      detected = patterns.nonEmpty,
      description = f"${patterns.size} suspicious patterns detected (avg severity: ${(avgPatternSeverity * 100).toInt}%)"
    ) :: factors

    // Factor 2: Payee trust ratio
    val totalPayees = acc.payees.size
    val trustedPayees = acc.payees.count(_.trusted)
    val trustRatio = if (totalPayees > 0) trustedPayees.toDouble / totalPayees else 1.0
    factors = RiskFactor(
      name = "Payee Trust Ratio",
      weight = (1.0 - trustRatio) * 0.15,
      detected = trustRatio < 0.5,
      description = f"${trustedPayees}/${totalPayees} payees are trusted (${(trustRatio * 100).toInt}%)"
    ) :: factors

    // Factor 3: Transaction consistency
    val last30Days = acc.transactions.filter(t => Duration.between(t.timestamp, Instant.now()).toDays < 30)
    val categoryVariety = last30Days.map(_.category).distinct.size
    val highVariety = categoryVariety > 5
    factors = RiskFactor(
      name = "Spending Consistency",
      weight = if (highVariety) 0.10 else 0.0,
      detected = highVariety,
      description = f"Transactions across ${categoryVariety} different categories"
    ) :: factors

    // Factor 4: Locked funds anomaly
    val lockedRatio = if (acc.totalBalance > 0) (acc.lockedFunds.values.sum / acc.totalBalance).toDouble else 0.0
    val highLocked = lockedRatio > 0.3
    factors = RiskFactor(
      name = "Locked Funds Ratio",
      weight = if (highLocked) 0.15 else 0.0,
      detected = highLocked,
      description = f"${(lockedRatio * 100).toInt}% of funds are locked"
    ) :: factors

    factors
  }

  /** Calculate weighted risk score */
  private def calculateWeightedScore(factors: List[RiskFactor]): Double = {
    val totalWeight = factors.map(_.weight).sum
    min(1.0, totalWeight)
  }

  /** Convert numeric score to risk level */
  private def scoreToLevel(score: Double): RiskLevel = {
    if (score >= CRITICAL_RISK_THRESHOLD) RiskLevel.Critical
    else if (score >= HIGH_RISK_THRESHOLD) RiskLevel.High
    else if (score >= MEDIUM_RISK_THRESHOLD) RiskLevel.Medium
    else RiskLevel.Low
  }

  /** Generate recommendation based on risk level and factors */
  private def generateRecommendation(level: RiskLevel, factors: List[RiskFactor]): String = {
    level match
      case RiskLevel.Critical =>
        "BLOCK transaction immediately. Require parent approval and verification. Potential fraud/account takeover."
      case RiskLevel.High =>
        "Require immediate parent approval before proceeding. Multiple high-risk factors detected."
      case RiskLevel.Medium =>
        "Flag for parent review. Consider one-tap approval requirement."
      case RiskLevel.Low =>
        "Proceed with normal validation. Monitor for pattern changes."
  }

  /** Get or create behavior baseline for an account */
  private def getOrCreateBaseline(acc: Account): BehaviorBaseline = {
    Repository.getBaseline(acc.id).getOrElse {
      // Create initial baseline from transaction history
      val txns = acc.transactions.filter(_.status == TransactionStatus.Completed)
      
      val avgDaily = if (txns.isEmpty) 0.0 else {
        val days = Duration.between(acc.createdAt, Instant.now()).toDays max 1
        txns.size.toDouble / days
      }
      
      val avgAmount = if (txns.isEmpty) BigDecimal(500) else {
        txns.map(_.amount).sum / txns.size
      }
      
      val categories = txns.groupBy(_.category).view.mapValues(_.size).toMap
      
      val timeRanges = inferCommonTimeRanges(txns)
      
      val payees = txns.flatMap(_.toPayeeId).toSet
      
      val baseline = BehaviorBaseline(
        accountId = acc.id,
        avgDailyTransactions = avgDaily,
        avgTransactionAmount = avgAmount,
        commonCategories = categories,
        commonTimeRanges = timeRanges,
        typicalPayees = payees
      )
      
      Repository.saveBaseline(baseline)
      baseline
    }
  }

  /** Infer common transaction time ranges from history */
  private def inferCommonTimeRanges(txns: List[Transaction]): List[TimeRange] = {
    val hours = txns.map(t => ZonedDateTime.ofInstant(t.timestamp, ZoneId.systemDefault()).getHour)
    val hourCounts = hours.groupBy(identity).view.mapValues(_.size).toMap
    
    // Find peak hours (those with above-average frequency)
    if (hourCounts.isEmpty) List(TimeRange(9, 21)) // default
    else {
      val avgCount = hourCounts.values.sum.toDouble / 24
      val peakHours = hourCounts.filter(_._2 > avgCount).keys.toList.sorted
      
      // Group consecutive hours into ranges
      if (peakHours.isEmpty) List(TimeRange(9, 21))
      else {
        var ranges = List.empty[TimeRange]
        var start = peakHours.head
        var end = start + 1
        
        peakHours.tail.foreach { hour =>
          if (hour == end) end = hour + 1
          else {
            ranges = TimeRange(start, end) :: ranges
            start = hour
            end = hour + 1
          }
        }
        ranges = TimeRange(start, end) :: ranges
        ranges.reverse
      }
    }
  }

  /** Store detected pattern for learning */
  private def storePattern(accountId: String, pattern: BehaviorPattern): Unit = {
    Repository.savePattern(accountId, pattern)
  }

  /** Get recent patterns for an account */
  private def getRecentPatterns(accountId: String, duration: Duration): List[BehaviorPattern] = {
    Repository.getPatterns(accountId, duration)
  }

  /** Update baseline with new transaction data (learning) */
  def updateBaseline(accountId: String): Unit = {
    Repository.getAccount(accountId).foreach { acc =>
      val baseline = getOrCreateBaseline(acc)
      
      // Recalculate with exponential moving average
      val recentTxns = acc.transactions.filter(t => 
        Duration.between(t.timestamp, Instant.now()).toDays < 30 &&
        t.status == TransactionStatus.Completed
      )
      
      if (recentTxns.nonEmpty) {
        val newAvgAmount = (baseline.avgTransactionAmount * 0.7) + 
                          ((recentTxns.map(_.amount).sum / recentTxns.size) * 0.3)
        
        val newCategories = baseline.commonCategories ++ 
                           recentTxns.groupBy(_.category).view.mapValues(_.size).toMap
        
        val newPayees = baseline.typicalPayees ++ recentTxns.flatMap(_.toPayeeId).toSet
        
        val updated = baseline.copy(
          avgTransactionAmount = newAvgAmount,
          commonCategories = newCategories,
          typicalPayees = newPayees,
          lastUpdated = Instant.now()
        )
        
        Repository.saveBaseline(updated)
      }
    }
  }
}
