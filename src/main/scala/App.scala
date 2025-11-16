import domain._
import services._
import java.time.{LocalDate, Instant, LocalTime}

/** Small demo wiring showing how to use the services. */
@main def runDemo(): Unit = {
  val monitor = new MonitoringService()
  val emergency = new EmergencyOverrideService(monitor)
  val spending = new SpendingControlService(monitor, emergency)
  val fraud = new FraudGuardService(monitor)
  val unknown = new UnknownPayeeTransferService(monitor, spending)
  val payeeSvc = new PayeeSafetyService(monitor, unknown, spending, emergency)
  val incoming = new IncomingCreditService(monitor)
  val withdraw = new WithdrawalService(monitor, emergency)
  val purpose = new PurposeOverrideService(monitor)
  val smart = new SmartModesService(monitor)

  // Clear repository & create demo parent/minor/account
  Repository.clearAll()
  val parent = Repository.saveParent(Parent(fullName = "Ramesh Gupta", email = Some("r@ex.com")))
  val minor = Repository.saveMinor(Minor(fullName = "Asha Gupta", dateOfBirth = LocalDate.of(2010, 5, 12), parentIds = List(parent.id)))
  val limits = Limits(monthly = Some(BigDecimal(5000)), perTransaction = Some(BigDecimal(2000)), perCategory = Map.empty, withdrawalLimits = WithdrawalLimits(daily = Some(BigDecimal(1000)), weekly = Some(BigDecimal(2000)), monthly = Some(BigDecimal(3000))))
  val account = Repository.saveAccount(Account(minorId = minor.id, balance = BigDecimal(1000), limits = limits))
  val attachedMinor = minor.attachAccount(account.id); Repository.saveMinor(attachedMinor)

  println(s"Created account ${account.id} with balance ₹${account.balance}")

  // 1) Incoming credit test: parent sends ₹8000 (should exceed computed limit and lock excess)
  println("\n-- Incoming credit ₹8000 --")
  val afterIn = incoming.processIncoming(account.id, BigDecimal(8000))
  println(s"Balance: ${afterIn.totalBalance}, Locked: ${afterIn.lockedFunds}")

  // 2) Try add payee at night (first allowed)
  println("\n-- Add payee at night (first) --")
  val nightTime = LocalTime.of(22, 0)
  payeeSvc.addPayee(account.id, "Friend Rahul", " ACC-12345 ", nightTime) match
    case Left(alert) => println(s"Add payee blocked: ${alert.message}")
    case Right(p) => println(s"Added payee ${p.displayName} id=${p.id}")

  // 3) First transfer to unknown payee up to ₹1000 allowed
  println("\n-- First transfer to unknown payee ₹900 --")
  val unknownPayeeId = "external-payee-1"
  val tx1 = Transaction(fromAccountId = account.id, toPayeeId = Some(unknownPayeeId), amount = BigDecimal(900), category = Category.Gifts)
  val res1 = unknown.handleFirstTransferToUnknownPayee(Repository.getAccount(account.id).get, tx1)
  println(s"Result: $res1")

  // 4) Attempt withdrawal exceeding daily limit
  println("\n-- Attempt withdrawal ₹2000 (daily limit 1000) --")
  withdraw.attemptWithdrawal(account.id, BigDecimal(2000)) match
    case Left(alert) => println(s"Withdrawal blocked: ${alert.message}")
    case Right(acc) => println(s"Withdrawal success, balance ${acc.balance}")

  // 5) Parent tags funds for Medical ₹5000
  println("\n-- Parent tags ₹5000 for Medical --")
  purpose.tagFundsForPurpose(account.id, PurposeTag.Medical, BigDecimal(5000), parent.id) match
    case Left(a) => println(s"Tagging failed: ${a.message}")
    case Right(acc) => println(s"Tagged; locked funds now: ${acc.lockedFunds}")

  // 6) Use purpose funds at merchant category Medical for ₹1200 (allowed to consume)
  println("\n-- Spending ₹1200 at Medical merchant using purpose funds --")
  val txMed = Transaction(fromAccountId = account.id, toPayeeId = Some("pharmacy-1"), amount = BigDecimal(1200), category = Category.Medical, purposeTag = Some(PurposeTag.Medical))
  purpose.tryConsumePurposeFunds(account.id, txMed, Some(Category.Medical)) match
    case Left(a) => println(s"Cannot use purpose funds: ${a.message}")
    case Right((acc, tx)) => println(s"Purpose funds used; account balance=${acc.balance}, locked=${acc.lockedFunds}")

  // show alerts
  println("\n-- Alerts for account --")
  Repository.getAlerts(account.id).reverse.foreach(a => println(s"${a.createdAt} | ${a.alertType} | ${a.message}"))
}
