import domain._
import services._
import java.time.{LocalDate, Instant, LocalTime}

/** Comprehensive demo showing all Youth Banking features. */
@main def runDemo(): Unit = {
  println("=" * 60)
  println("Youth Banking System - Comprehensive Demo")
  println("=" * 60)
  
  // Initialize all services
  val monitor = new MonitoringService()
  val aiEngine = new AIRiskEngine(monitor)
  val emergency = new EmergencyOverrideService(monitor)
  val spending = new SpendingControlService(monitor, emergency)
  val fraud = new FraudGuardService(monitor)
  val unknown = new UnknownPayeeTransferService(monitor, spending)
  val payeeSvc = new PayeeSafetyService(monitor, unknown, spending, emergency)
  val incoming = new IncomingCreditService(monitor)
  val withdraw = new WithdrawalService(monitor, emergency)
  val purpose = new PurposeOverrideService(monitor)
  val smart = new SmartModesService(monitor)

  println("✓ All services initialized")

  // Clear repository and start fresh
  Repository.clear()
  println("✓ Repository cleared")
  
  // Create parent (using simple version)
  val parentId = "parent_001"
  println(s"✓ Created parent: $parentId")
  
  // Create minor
  val minor = Minor(id = "minor_001", name = "Asha Gupta", age = 16)
  Repository.saveMinor(minor)
  println(s"✓ Created minor: ${minor.name}")
  
  // Create account with comprehensive limits
  val limits = Limits(
    monthly = Some(BigDecimal(5000)),
    perTransaction = Some(BigDecimal(2000)),
    perCategory = Map(
      Category.Food -> BigDecimal(2000),
      Category.Entertainment -> BigDecimal(1000)
    ),
    withdrawalLimits = WithdrawalLimits(
      daily = Some(BigDecimal(1000)),
      weekly = Some(BigDecimal(2000)),
      monthly = Some(BigDecimal(3000))
    )
  )
  
  val account = Account(
    minorId = minor.id,
    balance = BigDecimal(1000),
    limits = limits
  )
  
  Repository.saveAccount(account)
  Repository.linkAccountToParent(account.id, parentId)

  println(s"✓ Created account: ${account.id}")
  println(s"  Balance: ₹${account.balance}")
  println(s"  Monthly Limit: ₹${limits.monthly.getOrElse(0)}")
  println()

  // ========================================
  // TEST 1: Incoming Credit with Locking
  // ========================================
  println("=" * 60)
  println("TEST 1: Incoming Credit ₹8000 (Exceeds Limit)")
  println("=" * 60)
  
  val afterIn = incoming.processIncoming(account.id, BigDecimal(8000))
  println(s"✓ Credit processed")
  println(s"  Total Balance: ₹${afterIn.totalBalance}")
  println(s"  Available Balance: ₹${afterIn.availableBalance}")
  println(s"  Locked Funds: ₹${afterIn.lockedFunds.values.sum}")
  println()

  // ========================================
  // TEST 2: Add Payee at Night Time
  // ========================================
  println("=" * 60)
  println("TEST 2: Add Payee at Night (22:00)")
  println("=" * 60)
  
  val nightTime = LocalTime.of(22, 0)
  payeeSvc.addPayee(account.id, "Friend Rahul", "ACC-12345", nightTime) match {
    case Left(alert) => 
      println(s"⚠️  Add payee blocked: ${alert.message}")
    case Right(p) => 
      println(s"✓ Added payee: ${p.displayName}")
      println(s"  Payee ID: ${p.id}")
      println(s"  Account Number: ${p.accountNumber}")
  }
  println()

  // ========================================
  // TEST 3: First Transfer to Unknown Payee
  // ========================================
  println("=" * 60)
  println("TEST 3: First Transfer to Unknown Payee ₹900")
  println("=" * 60)
  
  val unknownPayeeId = "external-payee-1"
  val tx1 = Transaction(
    fromAccountId = account.id,
    toPayeeId = Some(unknownPayeeId),
    amount = BigDecimal(900),
    category = Category.Gifts,
    timestamp = Instant.now()
  )
  
  val res1 = unknown.handleFirstTransferToUnknownPayee(
    Repository.getAccount(account.id).get,
    tx1
  )
  
  res1 match {
    case Right((acc, tx)) =>
      println(s"✓ First transfer allowed (up to ₹1000)")
      println(s"  Amount: ₹${tx.amount}")
      println(s"  New Balance: ₹${acc.balance}")
    case Left(alert) =>
      println(s"✗ Transfer blocked: ${alert.message}")
  }
  println()

  // ========================================
  // TEST 4: Withdrawal Limit Check
  // ========================================
  println("=" * 60)
  println("TEST 4: Withdrawal ₹2000 (Daily Limit: ₹1000)")
  println("=" * 60)
  
  withdraw.attemptWithdrawal(account.id, BigDecimal(2000)) match {
    case Left(alert) => 
      println(s"⚠️  Withdrawal blocked: ${alert.message}")
    case Right(acc) => 
      println(s"✓ Withdrawal successful")
      println(s"  New Balance: ₹${acc.balance}")
  }
  println()

  // ========================================
  // TEST 5: Purpose Funds (Medical)
  // ========================================
  println("=" * 60)
  println("TEST 5: Tag ₹5000 for Medical Purpose")
  println("=" * 60)
  
  purpose.tagFundsForPurpose(
    account.id,
    PurposeTag.Medical,
    BigDecimal(5000),
    parentId
  ) match {
    case Left(alert) => 
      println(s"✗ Tagging failed: ${alert.message}")
    case Right(acc) => 
      println(s"✓ Funds tagged for Medical")
      println(s"  Tagged Amount: ₹5000")
      println(s"  Locked Funds: ₹${acc.lockedFunds.values.sum}")
  }
  println()

  // ========================================
  // TEST 6: Use Purpose Funds at Medical Merchant
  // ========================================
  println("=" * 60)
  println("TEST 6: Spend ₹1200 at Medical Merchant (Using Purpose Funds)")
  println("=" * 60)
  
  val txMed = Transaction(
    fromAccountId = account.id,
    toPayeeId = Some("pharmacy-1"),
    amount = BigDecimal(1200),
    category = Category.Medical,
    purposeTag = Some(PurposeTag.Medical),
    timestamp = Instant.now()
  )
  
  purpose.tryConsumePurposeFunds(account.id, txMed, Some(Category.Medical)) match {
    case Left(alert) => 
      println(s"✗ Cannot use purpose funds: ${alert.message}")
    case Right((acc, tx)) => 
      println(s"✓ Purpose funds consumed")
      println(s"  Spent: ₹${tx.amount}")
      println(s"  Balance: ₹${acc.balance}")
      println(s"  Remaining Locked: ₹${acc.lockedFunds.values.sum}")
  }
  println()

  // ========================================
  // TEST 7: Normal Transaction with Spending Limits
  // ========================================
  println("=" * 60)
  println("TEST 7: Normal Transaction ₹500 (Food)")
  println("=" * 60)
  
  val txFood = Transaction(
    fromAccountId = account.id,
    toPayeeId = Some("food-store-1"),
    amount = BigDecimal(500),
    category = Category.Food,
    timestamp = Instant.now()
  )
  
  spending.validateAndApplyTransaction(account.id, txFood) match {
    case Right((acc, tx)) =>
      println(s"✓ Transaction successful")
      println(s"  Category: ${tx.category}")
      println(s"  Amount: ₹${tx.amount}")
      println(s"  New Balance: ₹${acc.balance}")
    case Left(alert) =>
      println(s"✗ Transaction failed: ${alert.message}")
  }
  println()

  // ========================================
  // TEST 8: AI Risk Analysis
  // ========================================
  println("=" * 60)
  println("TEST 8: AI Risk Analysis")
  println("=" * 60)
  
  val updatedAccount = Repository.getAccount(account.id).get
  val riskScore = aiEngine.analyzeAccountBehavior(updatedAccount.id)
  
  println(s"Risk Assessment:")
  println(s"  Level: ${riskScore.level}")
  println(s"  Score: ${(riskScore.score * 100).toInt}%")
  println(s"  Recommendation: ${riskScore.recommendation}")
  println(s"  Factors: ${riskScore.factors.size} risk factors identified")
  println()

  // ========================================
  // TEST 9: Smart Mode Application
  // ========================================
  println("=" * 60)
  println("TEST 9: Apply School Hours Safe Mode")
  println("=" * 60)
  
  val schoolMode = SafetyMode.SchoolHoursSafe
  smart.applyMode(account.id, schoolMode)
  println(s"✓ Applied safety mode: $schoolMode")
  println()

  // ========================================
  // TEST 10: View All Alerts
  // ========================================
  println("=" * 60)
  println("Account Alerts Summary")
  println("=" * 60)
  
  val alerts = Repository.getAlerts(account.id)
  if (alerts.isEmpty) {
    println("No alerts generated")
  } else {
    println(s"Total Alerts: ${alerts.size}")
    println()
    alerts.reverse.take(10).foreach { alert =>
      println(s"[${alert.alertType}]")
      println(s"  ${alert.message}")
      println(s"  Time: ${alert.timestamp}")
      println(s"  Severity: ${alert.severity}")
      println()
    }
  }

  // ========================================
  // Repository Statistics
  // ========================================
  println("=" * 60)
  println("Repository Statistics")
  println("=" * 60)
  println(Repository.getStats)

  println("=" * 60)
  println("Demo Complete!")
  println("=" * 60)
}
