package services

import domain._
import java.time.Instant

/** Manages parent-tagged purpose funds; minor can spend those funds only at verified merchants in that category.
  * The model used here:
  *   - Parent calls tagFunds(account, tag, amount) -> funds become locked under that purpose (visible but usable only when a matching merchant category is used)
  *   - When minor makes a purchase at a merchant with matching category, funds from that locked purpose are used first
  */
class PurposeOverrideService(monitor: MonitoringService) {

  /** Parent tags funds by purpose; funds are locked until used by matching category. */
  def tagFundsForPurpose(accountId: String, purpose: PurposeTag, amount: BigDecimal, parentId: String): Either[Alert, Account] = {
    Repository.getAccount(accountId) match
      case None => Left(monitor.makeAlert(accountId, AlertType.GenericNotice, "Account missing"))
      case Some(acc) =>
        val updated = acc.lockFunds(purpose, amount).addFunds(amount) // add the funds then lock
        Repository.saveAccount(updated)
        monitor.makeAlert(acc.id, AlertType.PurposeUnlockRequested, s"Parent $parentId sent ₹$amount tagged as $purpose")
        Right(updated)
  }

  /** Attempt to consume purpose-locked funds for a transaction; returns updated account & tx if allowed. */
  def tryConsumePurposeFunds(accountId: String, tx: Transaction, merchantCategoryOpt: Option[Category]): Either[Alert, (Account, Transaction)] = {
    Repository.getAccount(accountId) match
      case None => Left(monitor.makeAlert(accountId, AlertType.GenericNotice, "Account missing"))
      case Some(acc) =>
        tx.purposeTag match
          case None =>
            // no purpose requested — normal flow
            Left(monitor.makeAlert(acc.id, AlertType.GenericNotice, "No purpose tag in transaction"))
          case Some(purpose) =>
            val lockedAmount = acc.lockedFunds.getOrElse(purpose, BigDecimal(0))
            if (lockedAmount <= 0) Left(monitor.makeAlert(acc.id, AlertType.GenericNotice, s"No locked funds for purpose $purpose"))
            else merchantCategoryOpt match
              case Some(cat) =>
                // only allow if merchant category belongs to purpose mapping (simple mapping here)
                val allowed = purposeMatchesCategory(purpose, cat)
                if (!allowed) Left(monitor.makeAlert(acc.id, AlertType.GenericNotice, s"Merchant category $cat not permitted for purpose $purpose"))
                else {
                  val consume = tx.amount min lockedAmount
                  val afterUnlock = acc.unlockFunds(purpose, consume)
                  val deducted = afterUnlock.deductFunds(tx.amount, tx.copy(status = TransactionStatus.Completed))
                  Repository.saveAccount(deducted)
                  Right((deducted, tx.copy(status = TransactionStatus.Completed)))
                }
              case None =>
                Left(monitor.makeAlert(acc.id, AlertType.GenericNotice, "Merchant category unknown; cannot use purpose-tagged funds"))
  }

  private def purposeMatchesCategory(p: PurposeTag, c: Category): Boolean = p match
    case PurposeTag.Medical => c == Category.Medical || c == Category.Grocery
    case PurposeTag.Travel  => c == Category.Travel || c == Category.Transport
    case PurposeTag.Emergency => true
    case PurposeTag.Education => c == Category.Education || c == Category.Other
    case PurposeTag.ExamFees => c == Category.Education
    case _ => true
}
