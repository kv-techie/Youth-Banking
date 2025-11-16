package examples

import domain._
import services._
import java.time.Instant

object AIRiskEngineDemo {
  def main(args: Array[String]): Unit = {
    // Setup services
    val monitor = new MonitoringService()
    val aiEngine = new AIRiskEngine(monitor)
    val emergency = new EmergencyOverrideService()
    val spendingService = new SpendingControlService(monitor, emergency)
    val unknownPayeeService = new UnknownPayeeTransferService(monitor)
    val payeeService = new PayeeSafetyService(monitor, unknownPayeeService, spendingService, emergency)
    val txController = new TransactionController(
      spendingService, payeeService, null, null, monitor, aiEngine
    )

    // Create test account
    val limits = Limits(
      monthly = Some(BigDecimal(10000)),
      perTransaction = Some(BigDecimal(2000)),
      withdrawalLimits = WithdrawalLimits(daily = Some(BigDecimal(1000)))
    )
    
    val account = Account(
      minorId = "minor123",
      balance = BigDecimal(15000),
      limits = limits
    )
    Repository.saveAccount(account)

    // Simulate normal transactions to build baseline
    println("=== Building Behavioral Baseline ===")
    for (i <- 1 to 10) {
      val tx = Transaction(
        fromAccountId = account.id,
        toPayeeId = Some(s"payee${i % 3}"), // Repeat payees
        amount = BigDecimal(200 + (i * 50)),
        category = Category.Food,
        timestamp = Instant.now().minusSeconds(86400 * (11 - i)) // Spread over 10 days
      )
      Repository.getAccount(account.id).foreach { acc =>
        Repository.saveAccount(acc.deductFunds(tx.amount, tx.copy(status = TransactionStatus.Completed)))
      }
    }

    aiEngine.updateBaseline(account.id)
    println(s"Baseline created for account ${account.id}")
    
    // Test 1: Normal transaction (should be low risk)
    println("\n=== Test 1: Normal Transaction ===")
    val normalTx = Transaction(
      fromAccountId = account.id,
      toPayeeId = Some("payee1"),
      amount = BigDecimal(250),
      category = Category.Food
    )
    val score1 = aiEngine.analyzeTransaction(account.id, normalTx)
    printRiskScore(score1)

    // Test 2: Sudden large transaction (should be high risk)
    println("\n=== Test 2: Sudden Large Transaction ===")
    val largeTx = Transaction(
      fromAccountId = account.id,
      toPayeeId = Some("payee1"),
      amount = BigDecimal(5000),
      category = Category.Food
    )
    val score2 = aiEngine.analyzeTransaction(account.id, largeTx)
    printRiskScore(score2)

    // Test 3: Night-time transaction to new payee (social engineering indicator)
    println("\n=== Test 3: Night-time + New Payee + Large Amount ===")
    val nightTx = Transaction(
      fromAccountId = account.id,
      toPayeeId = Some("suspicious_payee"),
      amount = BigDecimal(3000),
      category = Category.Other,
      timestamp = Instant.now().atZone(java.time.ZoneId.systemDefault())
        .withHour(2).toInstant() // 2 AM
    )
    val score3 = aiEngine.analyzeTransaction(account.id, nightTx)
    printRiskScore(score3)

    // Test 4: High velocity (many transactions)
    println("\n=== Test 4: High Velocity Attack ===")
    Repository.getAccount(account.id).foreach { acc =>
      for (i <- 1 to 7) {
        val tx = Transaction(
          fromAccountId = acc.id,
          toPayeeId = Some(s"velocity_payee$i"),
          amount = BigDecimal(100),
          category = Category.Other,
          timestamp = Instant.now().minusSeconds(60 * (8 - i))
        )
        Repository.saveAccount(acc.copy(transactions = tx :: acc.transactions))
      }
    }
    val velocityTx = Transaction(
      fromAccountId = account.id,
      toPayeeId = Some("velocity_payee8"),
      amount = BigDecimal(100),
      category = Category.Other
    )
    val score4 = aiEngine.analyzeTransaction(account.id, velocityTx)
    printRiskScore(score4)

    // Test 5: Overall account analysis
    println("\n=== Test 5: Account Risk Assessment ===")
    val accountScore = aiEngine.analyzeAccountBehavior(account.id)
    printRiskScore(accountScore)
  }

  def printRiskScore(score: RiskScore): Unit = {
    println(s"Risk Level: ${score.level}")
    println(f"Risk Score: ${score.score * 100}%.1f%%")
    println(s"Recommendation: ${score.recommendation}")
    println("Risk Factors:")
    score.factors.filter(_.detected).foreach { factor =>
      println(f"  - ${factor.name}: ${factor.weight * 100}%.1f%% weight - ${factor.description}")
    }
  }
}
