package services

import domain._
import java.time.{Instant, ZonedDateTime, ZoneId}

/** Enforces withdrawal limits (daily/weekly/monthly) and triggers parent approvals when exceeded. */
class WithdrawalService(monitor: MonitoringService, emergency: EmergencyOverrideService) {

  def attemptWithdrawal(accountId: String, amount: BigDecimal, now: Instant = Instant.now()): Either[Alert, Account] = {
    Repository.getAccount(accountId) match
      case None => Left(monitor.makeAlert(accountId, AlertType.GenericNotice, "Account missing"))
      case Some(acc) =>
        if (emergency.isActive(acc.id)) {
          val updated = acc.deductFunds(amount, Transaction(fromAccountId = acc.id, toPayeeId = None, amount = amount, category = Category.Other, timestamp = now, status = TransactionStatus.Completed))
          Repository.saveAccount(updated)
          Right(updated)
        } else {
          val wl = acc.limits.withdrawalLimits
          val dailySpent = sumWindow(acc.transactions, now, java.time.temporal.ChronoUnit.DAYS)
          val weeklySpent = sumWindow(acc.transactions, now, java.time.temporal.ChronoUnit.WEEKS)
          val monthlySpent = sumWindow(acc.transactions, now, java.time.temporal.ChronoUnit.MONTHS)

          wl.daily match
            case Some(max) if (dailySpent + amount) > max =>
              Left(monitor.makeAlert(acc.id, AlertType.WithdrawalExceeded, s"Daily withdrawal limit ₹$max exceeded"))
            case _ =>
              wl.weekly match
                case Some(max) if (weeklySpent + amount) > max =>
                  Left(monitor.makeAlert(acc.id, AlertType.WithdrawalExceeded, s"Weekly withdrawal limit ₹$max exceeded"))
                case _ =>
                  wl.monthly match
                    case Some(max) if (monthlySpent + amount) > max =>
                      Left(monitor.makeAlert(acc.id, AlertType.WithdrawalExceeded, s"Monthly withdrawal limit ₹$max exceeded"))
                    case _ =>
                      if (acc.availableBalance < amount) Left(monitor.makeAlert(acc.id, AlertType.FraudSuspected, s"Insufficient available balance"))
                      else {
                        val tx = Transaction(fromAccountId = acc.id, toPayeeId = None, amount = amount, category = Category.Other, timestamp = now, status = TransactionStatus.Completed)
                        val updated = acc.deductFunds(amount, tx)
                        Repository.saveAccount(updated)
                        Right(updated)
                      }
        }
  }

  private def sumWindow(txs: List[Transaction], now: Instant, unit: java.time.temporal.ChronoUnit): BigDecimal = {
    val z = ZonedDateTime.ofInstant(now, ZoneId.systemDefault())
    val filtered = unit match
      case java.time.temporal.ChronoUnit.DAYS =>
        txs.filter(t => java.time.Duration.between(t.timestamp, now).toDays < 1 && t.status == TransactionStatus.Completed)
      case java.time.temporal.ChronoUnit.WEEKS =>
        txs.filter(t => java.time.Duration.between(t.timestamp, now).toDays < 7 && t.status == TransactionStatus.Completed)
      case java.time.temporal.ChronoUnit.MONTHS =>
        val yr = z.getYear; val mo = z.getMonthValue
        txs.filter(t => {
          val tz = ZonedDateTime.ofInstant(t.timestamp, ZoneId.systemDefault())
          tz.getYear == yr && tz.getMonthValue == mo && t.status == TransactionStatus.Completed
        })
      case _ => Nil
    filtered.map(_.amount).foldLeft(BigDecimal(0))(_ + _)
  }
}
