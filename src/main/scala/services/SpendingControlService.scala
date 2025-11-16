package services

import domain._
import java.time.{Instant, ZoneId, ZonedDateTime}
import scala.util.Try

/** Validates and enforces per-transaction, per-category, and monthly limits. */
class SpendingControlService(monitor: MonitoringService, emergency: EmergencyOverrideService) {

  /** Validate transaction against limits; returns Right(updatedAccount, txMarked) or Left(alert). */
  def validateAndApplyTransaction(accountId: String, tx: Transaction): Either[Alert, (Account, Transaction)] = {
    Repository.getAccount(accountId) match
      case None =>
        val a = monitor.makeAlert(accountId, AlertType.GenericNotice, s"Account not found")
        Left(a)
      case Some(acc) =>
        // emergency override bypasses limits
        if (emergency.isActive(acc.id)) {
          val newAcc = acc.deductFunds(tx.amount, tx.copy(status = TransactionStatus.Completed))
          Repository.saveAccount(newAcc)
          Right((newAcc, tx.markStatus(TransactionStatus.Completed)))
        } else {
          val l = acc.limits

          // per-transaction limit
          l.perTransaction match
            case Some(max) if tx.amount > max =>
              val a = monitor.makeAlert(acc.id, AlertType.LargeTransaction, s"Transaction ₹${tx.amount} exceeds per-transaction limit ₹$max")
              Left(a)
            case _ =>
              // per-category
              val catLimitOpt = l.perCategory.get(tx.category)
              catLimitOpt match
                case Some(catMax) =>
                  // compute spent this month for category
                  val monthSpent = sumMonthForCategory(acc.transactions, tx.category, tx.timestamp)
                  if (monthSpent + tx.amount > catMax) {
                    val a = monitor.makeAlert(acc.id, AlertType.LargeTransaction, s"Category ${tx.category} monthly limit exceeded (₹$catMax). Already spent ₹$monthSpent")
                    Left(a)
                  } else tryDeduct(acc, tx)
                case None =>
                  // monthly limit fallback
                  l.monthly match
                    case Some(monthlyMax) =>
                      val monthTotal = sumMonthAll(acc.transactions, tx.timestamp)
                      if (monthTotal + tx.amount > monthlyMax) {
                        val a = monitor.makeAlert(acc.id, AlertType.LargeTransaction, s"Monthly limit ₹$monthlyMax exceeded. Already spent ₹$monthTotal")
                        Left(a)
                      } else tryDeduct(acc, tx)
                    case None =>
                      tryDeduct(acc, tx)
      }

  private def tryDeduct(acc: Account, tx: Transaction): Either[Alert, (Account, Transaction)] = {
    if (acc.availableBalance < tx.amount) {
      val a = monitor.makeAlert(acc.id, AlertType.FraudSuspected, s"Insufficient available balance for ₹${tx.amount}")
      Left(a)
    } else {
      val completed = tx.copy(status = TransactionStatus.Completed)
      val updated = acc.deductFunds(tx.amount, completed)
      Repository.saveAccount(updated)
      Right((updated, completed))
    }
  }

  private def sumMonthForCategory(txs: List[Transaction], cat: Category, time: Instant): BigDecimal = {
    val z = ZonedDateTime.ofInstant(time, ZoneId.systemDefault())
    val year = z.getYear; val month = z.getMonthValue
    txs.filter(t => {
      val tz = ZonedDateTime.ofInstant(t.timestamp, ZoneId.systemDefault())
      tz.getYear == year && tz.getMonthValue == month && t.category == cat && t.status == TransactionStatus.Completed
    }).map(_.amount).foldLeft(BigDecimal(0))(_ + _)
  }

  private def sumMonthAll(txs: List[Transaction], time: Instant): BigDecimal = {
    val z = ZonedDateTime.ofInstant(time, ZoneId.systemDefault())
    val year = z.getYear; val month = z.getMonthValue
    txs.filter(t => {
      val tz = ZonedDateTime.ofInstant(t.timestamp, ZoneId.systemDefault())
      tz.getYear == year && tz.getMonthValue == month && t.status == TransactionStatus.Completed
    }).map(_.amount).foldLeft(BigDecimal(0))(_ + _)
  }
}
