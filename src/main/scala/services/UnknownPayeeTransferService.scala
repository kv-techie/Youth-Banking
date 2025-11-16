package services

import domain._
import java.time.Instant

/** Handles the special unknown-payee flows:
  * - allow first transfer up to ₹1000 without parent approval
  * - subsequent transfers require approval and risk assessment
  */
class UnknownPayeeTransferService(monitor: MonitoringService, spendingService: SpendingControlService) {

  def handleFirstTransferToUnknownPayee(account: Account, tx: Transaction): Either[Alert, (Account, Transaction)] = {
    val isFirst = account.transactions.forall(_.toPayeeId.getOrElse("") != tx.toPayeeId.getOrElse(""))
    if (isFirst && tx.amount <= BigDecimal(1000)) {
      // allow via spending service
      spendingService.validateAndApplyTransaction(account.id, tx)
    } else {
      // create alert and mark RequiresParentApproval
      val alert = monitor.makeAlert(account.id, AlertType.RepeatedPaymentsToUnknownPayee, s"Transfer to unknown payee ₹${tx.amount} requires parent approval")
      Left(alert)
    }
  }
}
