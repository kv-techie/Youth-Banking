package services

import domain._
import java.time.Instant

/** Handles incoming credits and locks funds when exceeding computed allowed incoming limit:
  * Formula used per your spec: (Monthly Transaction Limit + Monthly Withdrawal Limit) × 2
  */
class IncomingCreditService(monitor: MonitoringService) {

  /** Process incoming credit; returns updated Account */
  def processIncoming(accountId: String, amount: BigDecimal, txTime: Instant = Instant.now()): Account = {
    Repository.getAccount(accountId) match
      case None =>
        throw new IllegalArgumentException(s"Account $accountId not found")
      case Some(acc) =>
        val computedLimit = computeIncomingLimit(acc)
        val withAdded = acc.addFunds(amount)
        Repository.saveAccount(withAdded)
        if (withAdded.totalBalance > computedLimit) {
          // lock the excess portion
          val excess = (withAdded.totalBalance - computedLimit)
          val lockedAcc = withAdded.lockFunds(PurposeTag.Misc, excess)
          Repository.saveAccount(lockedAcc)
          monitor.makeAlert(acc.id, AlertType.IncomingCreditExceeded, s"Incoming funds exceeded computed limit ₹$computedLimit. Locked ₹$excess")
          lockedAcc
        } else withAdded
  }

  /** Compute incoming limit per account */
  def computeIncomingLimit(acc: Account): BigDecimal = {
    val monthlyTxnLimit = acc.limits.monthly.getOrElse(BigDecimal(0))
    val monthlyWithdraw = acc.limits.withdrawalLimits.monthly.getOrElse(BigDecimal(0))
    val base = monthlyTxnLimit + monthlyWithdraw
    base * BigDecimal(acc.limits.incomingCreditMultiplier)
  }
}
