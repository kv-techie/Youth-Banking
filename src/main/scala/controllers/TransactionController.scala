package controllers

import domain._
import services._
import java.time.Instant

/** Orchestrates transaction processing with AI risk analysis
  * 
  * This controller integrates all transaction-related services and adds
  * AI-driven risk assessment before processing transactions.
  */
class TransactionController(
  spendingService: SpendingControlService,
  payeeService: PayeeSafetyService,
  withdrawalService: WithdrawalService,
  purposeService: PurposeOverrideService,
  incomingCreditService: IncomingCreditService,
  monitor: MonitoringService,
  aiEngine: AIRiskEngine
) {

  /** Process a transaction with AI risk analysis
    * 
    * Flow:
    * 1. Run AI risk analysis on the transaction
    * 2. Based on risk level, either block, require approval, or proceed
    * 3. Apply appropriate validation and business rules
    * 4. Execute the transaction if all checks pass
    */
  def processTransaction(accountId: String, tx: Transaction): Either[Alert, (Account, Transaction)] = {
    // Step 1: AI Risk Analysis
    val riskScore = aiEngine.analyzeTransaction(accountId, tx)
    
    // Step 2: Handle based on risk level
    riskScore.level match
      case RiskLevel.Critical =>
        // BLOCK transaction immediately and alert parent
        val alert = Alert(
          accountId = accountId,
          alertType = AlertType.HighRiskScore,
          message = s"CRITICAL RISK DETECTED: ${riskScore.recommendation}",
          riskScore = Some(riskScore),
          requiresAction = true
        )
        monitor.sendAlert(accountId, alert)
        Left(alert)
        
      case RiskLevel.High =>
        // Require mandatory parent approval before proceeding
        val alert = Alert(
          accountId = accountId,
          alertType = AlertType.RequiresParentApproval,
          message = s"High-risk transaction detected. Parent approval required. ${riskScore.recommendation}",
          riskScore = Some(riskScore),
          requiresAction = true
        )
        monitor.sendAlert(accountId, alert)
        Left(alert)
        
      case RiskLevel.Medium =>
        // Flag for parent review but allow if within limits
        val alert = Alert(
          accountId = accountId,
          alertType = AlertType.BehavioralAnomaly,
          message = s"Medium-risk transaction: ${riskScore.recommendation}",
          riskScore = Some(riskScore),
          requiresAction = false
        )
        monitor.sendAlert(accountId, alert)
        // Continue with normal processing
        proceedWithTransaction(accountId, tx)
        
      case RiskLevel.Low =>
        // Normal processing - low risk
        proceedWithTransaction(accountId, tx)
  }

  /** Process transaction with standard validation flow */
  private def proceedWithTransaction(accountId: String, tx: Transaction): Either[Alert, (Account, Transaction)] = {
    // Determine transaction type and route to appropriate service
    if (tx.purposeTag.isDefined) {
      // Purpose-tagged transaction (parent sent money for specific use)
      purposeService.tryConsumePurposeFunds(accountId, tx, Some(tx.category))
    } else if (tx.toPayeeId.isDefined) {
      // Transfer to a payee
      payeeService.processTransfer(accountId, tx.toPayeeId, tx)
    } else {
      // Regular spending (no specific payee)
      spendingService.validateAndApplyTransaction(accountId, tx)
    }
  }

  /** Process withdrawal with risk analysis */
  def processWithdrawal(accountId: String, amount: BigDecimal, now: Instant = Instant.now()): Either[Alert, Account] = {
    // Create a withdrawal transaction for risk analysis
    val withdrawalTx = Transaction(
      fromAccountId = accountId,
      toPayeeId = None,
      amount = amount,
      category = Category.Other,
      timestamp = now,
      status = TransactionStatus.Pending
    )
    
    // Run risk analysis
    val riskScore = aiEngine.analyzeTransaction(accountId, withdrawalTx)
    
    riskScore.level match
      case RiskLevel.Critical | RiskLevel.High =>
        val alert = Alert(
          accountId = accountId,
          alertType = AlertType.HighRiskScore,
          message = s"High-risk withdrawal detected: ${riskScore.recommendation}",
          riskScore = Some(riskScore),
          requiresAction = true
        )
        monitor.sendAlert(accountId, alert)
        Left(alert)
      case _ =>
        // Proceed with withdrawal validation
        withdrawalService.attemptWithdrawal(accountId, amount, now)
  }

  /** Process incoming credit/deposit */
  def processIncomingCredit(accountId: String, amount: BigDecimal, fromSource: String, timestamp: Instant = Instant.now()): Account = {
    // Process through incoming credit service (handles locking excess funds)
    val updatedAccount = incomingCreditService.processIncoming(accountId, amount, timestamp)
    
    // Check if this incoming credit is unusual and needs AI analysis
    Repository.getAccount(accountId).foreach { acc =>
      val baseline = Repository.getBaseline(accountId)
      baseline.foreach { bl =>
        // If incoming amount is significantly larger than average transaction
        if (amount > bl.avgTransactionAmount * 5) {
          val alert = Alert(
            accountId = accountId,
            alertType = AlertType.BaselineDeviation,
            message = s"Unusually large incoming credit of ₹$amount from $fromSource (baseline: ₹${bl.avgTransactionAmount})",
            requiresAction = false
          )
          monitor.sendAlert(accountId, alert)
        }
      }
    }
    
    updatedAccount
  }

  /** Batch analyze account for scheduled risk assessment
    * 
    * This should be called periodically (e.g., daily) to assess overall account health
    */
  def analyzeAccount(accountId: String): RiskScore = {
    val score = aiEngine.analyzeAccountBehavior(accountId)
    
    // If high risk detected, send alert to parent
    if (score.level == RiskLevel.High || score.level == RiskLevel.Critical) {
      val alert = Alert(
        accountId = accountId,
        alertType = AlertType.BehavioralAnomaly,
        message = s"Account risk assessment: ${score.recommendation}",
        riskScore = Some(score),
        requiresAction = true
      )
      monitor.sendAlert(accountId, alert)
    }
    
    // Update baseline for continuous learning
    aiEngine.updateBaseline(accountId)
    
    score
  }

  /** Approve a pending high-risk transaction (parent override)
    * 
    * This allows parents to approve transactions that were flagged as high-risk
    */
  def approveTransaction(accountId: String, tx: Transaction, parentId: String): Either[Alert, (Account, Transaction)] = {
    // Log parent approval
    val alert = Alert(
      accountId = accountId,
      alertType = AlertType.GenericNotice,
      message = s"Parent $parentId approved high-risk transaction of ₹${tx.amount}",
      requiresAction = false
    )
    monitor.sendAlert(accountId, alert)
    
    // Process transaction without risk check (parent override)
    proceedWithTransaction(accountId, tx)
  }

  /** Get risk score for a hypothetical transaction (preview mode)
    * 
    * Useful for showing users the risk level before they commit to a transaction
    */
  def previewTransactionRisk(accountId: String, tx: Transaction): RiskScore = {
    aiEngine.analyzeTransaction(accountId, tx)
  }

  /** Get recent alerts for an account */
  def getAlerts(accountId: String, limit: Int = 10): List[Alert] = {
    Repository.getAlerts(accountId).take(limit)
  }

  /** Get high-priority alerts only */
  def getHighPriorityAlerts(accountId: String): List[Alert] = {
    Repository.getAlerts(accountId).filter(_.isHighPriority)
  }

  /** Mark patterns as resolved (after parent review) */
  def acknowledgePatterns(accountId: String): Unit = {
    Repository.clearPatterns(accountId)
  }

  /** Get account statistics including risk metrics */
  def getAccountStats(accountId: String): Option[AccountStats] = {
    Repository.getAccount(accountId).map { acc =>
      val baseline = Repository.getBaseline(accountId)
      val recentPatterns = Repository.getPatterns(accountId, java.time.Duration.ofDays(7))
      val recentAlerts = Repository.getAlerts(accountId).take(10)
      
      AccountStats(
        accountId = acc.id,
        balance = acc.totalBalance,
        availableBalance = acc.availableBalance,
        lockedFunds = acc.lockedFunds.values.sum,
        totalTransactions = acc.transactions.size,
        trustedPayees = acc.payees.count(_.trusted),
        totalPayees = acc.payees.size,
        recentPatternCount = recentPatterns.size,
        highPriorityAlertCount = recentAlerts.count(_.isHighPriority),
        avgTransactionAmount = baseline.map(_.avgTransactionAmount),
        lastRiskAssessment = baseline.map(_.lastUpdated)
      )
    }
  }
}

/** Account statistics including risk metrics */
case class AccountStats(
  accountId: String,
  balance: BigDecimal,
  availableBalance: BigDecimal,
  lockedFunds: BigDecimal,
  totalTransactions: Int,
  trustedPayees: Int,
  totalPayees: Int,
  recentPatternCount: Int,
  highPriorityAlertCount: Int,
  avgTransactionAmount: Option[BigDecimal],
  lastRiskAssessment: Option[java.time.Instant]
) {
  override def toString: String = {
    s"""Account Statistics ($accountId):
       |  Balance: ₹$balance (Available: ₹$availableBalance, Locked: ₹$lockedFunds)
       |  Transactions: $totalTransactions
       |  Payees: $trustedPayees trusted / $totalPayees total
       |  Risk Metrics:
       |    - Recent Patterns: $recentPatternCount
       |    - High Priority Alerts: $highPriorityAlertCount
       |    - Avg Transaction: ${avgTransactionAmount.map(a => s"₹$a").getOrElse("N/A")}
       |    - Last Risk Check: ${lastRiskAssessment.map(_.toString).getOrElse("Never")}
       |""".stripMargin
  }
}
