package services

import domain._
import java.time.{Instant, LocalTime}

/** Enforces add-payee rules and first-transaction rules depending on time of day.
  * Normal hours: 07:00 - 21:00
  * Night hours: 21:00 - 07:00
  */
class PayeeSafetyService(monitor: MonitoringService, unknownPayeeService: UnknownPayeeTransferService, spendingService: SpendingControlService, emergency: EmergencyOverrideService) {
  private val normalStart = LocalTime.of(7, 0)
  private val normalEnd   = LocalTime.of(21, 0)

  def isNormalHours(now: java.time.LocalTime): Boolean =
    !now.isBefore(normalStart) && now.isBefore(normalEnd)

  /** Add a new payee; enforces night rules. */
  def addPayee(accountId: String, displayName: String, acctNum: String, now: java.time.LocalTime = java.time.LocalTime.now()): Either[Alert, Payee] = {
    Repository.getAccount(accountId) match
      case None => Left(monitor.makeAlert(accountId, AlertType.GenericNotice, "Account not found"))
      case Some(acc) =>
        if (isNormalHours(now)) {
          val p = Payee(displayName = displayName, accountNumber = acctNum)
          val updated = acc.upsertPayee(p)
          Repository.saveAccount(updated)
          monitor.makeAlert(acc.id, AlertType.NewPayeeAdded, s"Payee ${displayName} added during normal hours")
          Right(p)
        } else {
          // night rules: only one new payee per night permitted
          val windowStart = Instant.now().minusSeconds(60 * 60 * 12) // last 12 hours approx night window
          val lastNightAdds = acc.payees.count(p => p.addedAt.isAfter(windowStart))
          if (lastNightAdds >= 1) {
            val a = monitor.makeAlert(acc.id, AlertType.NightTimeActivity, s"Night-time payee addition blocked; only one allowed per night")
            Left(a)
          } else {
            val p = Payee(displayName = displayName, accountNumber = acctNum)
            val updated = acc.upsertPayee(p)
            Repository.saveAccount(updated)
            monitor.makeAlert(acc.id, AlertType.NewPayeeAdded, s"Payee ${displayName} added during night hours (first allowed)")
            Right(p)
          }
        }
  }

  /** Process a transfer to a payee; handles unknown payee flows and first-transaction ₹1000 rule. */
  def processTransfer(accountId: String, payeeIdOpt: Option[String], tx: Transaction, now: java.time.LocalTime = java.time.LocalTime.now()): Either[Alert, (Account, Transaction)] = {
    Repository.getAccount(accountId) match
      case None => Left(monitor.makeAlert(accountId, AlertType.GenericNotice, s"Account missing"))
      case Some(acc) =>
        payeeIdOpt match
          case Some(payeeId) =>
            acc.payees.find(_.id == payeeId) match
              case None =>
                // unknown payee
                unknownPayeeService.handleFirstTransferToUnknownPayee(acc, tx)
              case Some(p) =>
                val isNight = !isNormalHours(now)
                if (!p.trusted && isNight) {
                  // night; only one first transaction of up to ₹1000 is allowed for a new payee
                  val isFirst = acc.transactions.forall(_.toPayeeId.getOrElse("") != payeeId)
                  if (isFirst && tx.amount <= BigDecimal(1000)) {
                    spendingService.validateAndApplyTransaction(acc.id, tx)
                  } else {
                    monitor.makeAlert(acc.id, AlertType.NightTimeActivity, s"Transfer to ${p.displayName} requires parental approval (night restrictions)")
                    Left(monitor.makeAlert(acc.id, AlertType.RequiresParentApproval, s"Transfer to ${p.displayName} blocked pending parental approval"))
                  }
                } else {
                  // normal rules: if untrusted and amount > 1000 then require approval
                  val isFirst = acc.transactions.forall(_.toPayeeId.getOrElse("") != payeeId)
                  if (isFirst && tx.amount <= BigDecimal(1000)) spendingService.validateAndApplyTransaction(acc.id, tx)
                  else if (!p.trusted && tx.amount > BigDecimal(1000)) {
                    Left(monitor.makeAlert(acc.id, AlertType.RepeatedPaymentsToUnknownPayee, s"Transfer ₹${tx.amount} to untrusted payee ${p.displayName} requires parental approval"))
                  } else {
                    spendingService.validateAndApplyTransaction(acc.id, tx)
                  }
                }
          case None =>
            // cash withdrawal or other — delegate elsewhere
            Left(monitor.makeAlert(accountId, AlertType.GenericNotice, s"Missing payeeId in transfer"))
  }
}
