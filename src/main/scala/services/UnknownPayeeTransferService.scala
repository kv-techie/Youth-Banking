package services

import domain._
import java.time.Instant

/** Handles the special unknown-payee flows:
  * - Allow first transfer up to ₹1000 without parent approval
  * - Subsequent transfers require approval and risk assessment
  * - Track unknown payee interactions for fraud detection
  */
class UnknownPayeeTransferService(
  monitor: MonitoringService, 
  spendingService: SpendingControlService
) {

  /** Maximum amount allowed for first transfer to unknown payee */
  private val FIRST_TRANSFER_LIMIT = BigDecimal(1000)

  /** Handle transfer to unknown payee with graduated trust system
    * 
    * @param account The account making the transfer
    * @param tx The transaction to unknown payee
    * @return Either an alert requiring approval, or successful transaction
    */
  def handleFirstTransferToUnknownPayee(
    account: Account, 
    tx: Transaction
  ): Either[Alert, (Account, Transaction)] = {
    
    val payeeId = tx.toPayeeId.getOrElse("")
    
    // Check if this is the first time transferring to this payee
    val isFirstTransfer = account.transactions.forall { prevTx =>
      prevTx.toPayeeId.getOrElse("") != payeeId
    }
    
    if (isFirstTransfer && tx.amount <= FIRST_TRANSFER_LIMIT) {
      // First transfer within limit - allow with notification
      monitor.makeAlert(
        account.id,
        AlertType.UnknownPayeeTransaction,
        s"First transfer to unknown payee: ₹${tx.amount}. Within ₹$FIRST_TRANSFER_LIMIT limit."
      )
      
      // Process through spending service
      spendingService.validateAndApplyTransaction(account.id, tx)
      
    } else if (isFirstTransfer && tx.amount > FIRST_TRANSFER_LIMIT) {
      // First transfer exceeds limit - requires approval
      Left(createApprovalAlert(
        account.id,
        tx,
        s"First transfer to unknown payee ₹${tx.amount} exceeds ₹$FIRST_TRANSFER_LIMIT limit. Parent approval required."
      ))
      
    } else {
      // Repeated transfer to unknown payee - requires approval
      val previousCount = account.transactions.count { prevTx =>
        prevTx.toPayeeId.contains(payeeId)
      }
      
      Left(createApprovalAlert(
        account.id,
        tx,
        s"Repeated transfer (#${previousCount + 1}) to unknown payee ₹${tx.amount}. Parent approval required."
      ))
    }
  }

  /** Check if payee is known/trusted in the account */
  def isKnownPayee(account: Account, payeeId: String): Boolean = {
    account.payees.exists(_.id == payeeId)
  }

  /** Check if payee is trusted (approved by parent) */
  def isTrustedPayee(account: Account, payeeId: String): Boolean = {
    account.payees.exists(p => p.id == payeeId && p.trusted)
  }

  /** Validate transfer to unknown payee with full checks */
  def validateUnknownTransfer(
    account: Account,
    tx: Transaction
  ): Either[Alert, Unit] = {
    
    val payeeId = tx.toPayeeId.getOrElse("")
    
    // If payee is already in account's payee list, it's not "unknown"
    if (isKnownPayee(account, payeeId)) {
      return Right(())
    }
    
    // Count previous transfers to this payee
    val transferCount = account.transactions.count { prevTx =>
      prevTx.toPayeeId.contains(payeeId) && 
      prevTx.status == TransactionStatus.Completed
    }
    
    // First transfer validation
    if (transferCount == 0) {
      if (tx.amount <= FIRST_TRANSFER_LIMIT) {
        Right(())
      } else {
        Left(createApprovalAlert(
          account.id,
          tx,
          s"First transfer amount ₹${tx.amount} exceeds ₹$FIRST_TRANSFER_LIMIT limit"
        ))
      }
    } else {
      // Subsequent transfers always need approval
      Left(createApprovalAlert(
        account.id,
        tx,
        s"Transfer #${transferCount + 1} to unknown payee requires approval"
      ))
    }
  }

  /** Get transfer history to a specific payee */
  def getPayeeTransferHistory(
    account: Account,
    payeeId: String
  ): List[Transaction] = {
    account.transactions.filter { tx =>
      tx.toPayeeId.contains(payeeId)
    }.sortBy(_.timestamp.toEpochMilli).reverse
  }

  /** Get statistics for unknown payee transfers */
  def getUnknownPayeeStats(account: Account): UnknownPayeeStats = {
    val unknownTransfers = account.transactions.filter { tx =>
      tx.toPayeeId.exists(payeeId => !isKnownPayee(account, payeeId))
    }
    
    val totalUnknownTransfers = unknownTransfers.size
    val totalUnknownAmount = unknownTransfers
      .filter(_.status == TransactionStatus.Completed)
      .map(_.amount)
      .sum
    
    val uniqueUnknownPayees = unknownTransfers
      .flatMap(_.toPayeeId)
      .distinct
      .size
    
    UnknownPayeeStats(
      totalTransfers = totalUnknownTransfers,
      totalAmount = totalUnknownAmount,
      uniquePayees = uniqueUnknownPayees,
      averageAmount = if (totalUnknownTransfers > 0) 
        totalUnknownAmount / totalUnknownTransfers 
      else BigDecimal(0)
    )
  }

  /** Check if account has suspicious unknown payee activity */
  def detectSuspiciousActivity(account: Account): Option[Alert] = {
    val stats = getUnknownPayeeStats(account)
    
    // Suspicious pattern: Multiple unknown payees in short time
    if (stats.uniquePayees > 5) {
      return Some(createAlert(
        account.id,
        AlertType.SuspiciousActivity,
        s"Suspicious: ${stats.uniquePayees} different unknown payees detected"
      ))
    }
    
    // Suspicious pattern: High total amount to unknown payees
    if (stats.totalAmount > BigDecimal(10000)) {
      return Some(createAlert(
        account.id,
        AlertType.SuspiciousActivity,
        s"Suspicious: Total ₹${stats.totalAmount} sent to unknown payees"
      ))
    }
    
    None
  }

  /** Promote unknown payee to trusted after parent approval */
  def promoteToTrustedPayee(
    accountId: String,
    payeeId: String,
    payeeName: String
  ): Either[String, Account] = {
    Repository.getAccount(accountId) match {
      case None => Left("Account not found")
      case Some(account) =>
        // Check if payee already exists
        if (isKnownPayee(account, payeeId)) {
          // Update existing payee to trusted
          val updatedPayees = account.payees.map { p =>
            if (p.id == payeeId) p.copy(trusted = true)
            else p
          }
          val updated = account.copy(payees = updatedPayees)
          Repository.saveAccount(updated)
          Right(updated)
        } else {
          // Add as new trusted payee
          val newPayee = Payee(
            id = payeeId,
            displayName = payeeName,
            accountNumber = payeeId,
            trusted = true
          )
          val updated = account.copy(payees = newPayee :: account.payees)
          Repository.saveAccount(updated)
          
          monitor.makeAlert(
            accountId,
            AlertType.NewPayeeAdded,
            s"Unknown payee '$payeeName' promoted to trusted after parent approval"
          )
          
          Right(updated)
        }
    }
  }

  /** Create an alert requiring parent approval */
  private def createApprovalAlert(
    accountId: String,
    tx: Transaction,
    message: String
  ): Alert = {
    Alert(
      accountId = accountId,
      alertType = AlertType.RequiresParentApproval,
      message = message,
      timestamp = Instant.now(),
      riskScore = None,
      requiresAction = true
    )
  }

  /** Create a generic alert */
  private def createAlert(
    accountId: String,
    alertType: AlertType,
    message: String
  ): Alert = {
    Alert(
      accountId = accountId,
      alertType = alertType,
      message = message,
      timestamp = Instant.now(),
      riskScore = None,
      requiresAction = false
    )
  }
}

/** Statistics for unknown payee transfers */
case class UnknownPayeeStats(
  totalTransfers: Int,
  totalAmount: BigDecimal,
  uniquePayees: Int,
  averageAmount: BigDecimal
) {
  override def toString: String = {
    s"""Unknown Payee Statistics:
       |  Total Transfers: $totalTransfers
       |  Total Amount: ₹$totalAmount
       |  Unique Payees: $uniquePayees
       |  Average Amount: ₹$averageAmount
       |""".stripMargin
  }
}
