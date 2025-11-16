package services

import domain._
import java.time.Instant

/** Simple rule-based fraud engine:
  * - Detect repeated failed attempts (we mark failed transactions)
  * - Detect rapid payments to same unknown payee
  * - Flag high-risk merchant categories (Gambling, Crypto)
  */
class FraudGuardService(monitor: MonitoringService) {

  def scanTransaction(account: Account, tx: Transaction): Unit = {
    // high-risk categories
    if (tx.category == Category.Gambling || tx.category == Category.Crypto)
      monitor.makeAlert(account.id, AlertType.FraudSuspected, s"High-risk merchant category ${tx.category} used in tx ${tx.id}")

    // repeated payments to same unknown payee in short time
    tx.toPayeeId.foreach { pid =>
      val recent = account.transactions.take(10)
      val repeated = recent.count(t => t.toPayeeId.contains(pid) && t.status == TransactionStatus.Completed)
      if (repeated >= 3)
        monitor.makeAlert(account.id, AlertType.FraudSuspected, s"Multiple recent payments to same payee $pid")
    }
  }

  def onFailedAuth(account: Account, details: String): Unit =
    monitor.makeAlert(account.id, AlertType.FraudSuspected, s"Failed auth detected: $details")
}
