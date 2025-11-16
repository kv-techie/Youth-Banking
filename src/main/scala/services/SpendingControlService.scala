package services

import domain._
import java.time.{Instant, ZoneId, ZonedDateTime}

/** Validates and enforces per-transaction, per-category, and monthly limits. */
class SpendingControlService(monitor: MonitoringService, emergency: EmergencyOverrideService) {

  /** Validate transaction against limits; returns Right(updatedAccount, txMarked) or Left(alert). */
  def validateAndApplyTransaction(accountId: String, tx: Transaction): Either[Alert, (Account, Transaction)] = {
    Repository.getAccount(accountId) match {
      case None =>
        val alert = createAlert(accountId, AlertType.TransactionBlocked, "Account not found")
        Left(alert)
        
      case Some(acc) =>
        // Emergency override bypasses limits
        if (emergency.isActive(acc.id)) {
          tryDeduct(acc, tx)
        } else {
          // Validate all limits
          validateLimits(acc, tx) match {
            case Some(alert) => Left(alert)
            case None => tryDeduct(acc, tx)
          }
        }
    }
  }

  /** Validate all spending limits */
  private def validateLimits(acc: Account, tx: Transaction): Option[Alert] = {
    val limits = acc.limits
    
    // 1. Per-transaction limit check
    limits.perTransaction match {
      case Some(max) if tx.amount > max =>
        return Some(createAlert(
          acc.id, 
          AlertType.LimitExceeded, 
          s"Transaction ₹${tx.amount} exceeds per-transaction limit ₹$max"
        ))
      case _ => // Continue
    }
    
    // 2. Per-category limit check
    limits.perCategory.get(tx.category) match {
      case Some(catMax) =>
        val monthSpent = sumMonthForCategory(acc.transactions, tx.category, tx.timestamp)
        if (monthSpent + tx.amount > catMax) {
          return Some(createAlert(
            acc.id, 
            AlertType.CategoryLimitReached, 
            s"Category ${tx.category} monthly limit exceeded (₹$catMax). Already spent ₹$monthSpent"
          ))
        }
      case None => // No category-specific limit
    }
    
    // 3. Monthly limit check
    limits.monthly match {
      case Some(monthlyMax) =>
        val monthTotal = sumMonthAll(acc.transactions, tx.timestamp)
        if (monthTotal + tx.amount > monthlyMax) {
          return Some(createAlert(
            acc.id, 
            AlertType.LimitExceeded, 
            s"Monthly limit ₹$monthlyMax exceeded. Already spent ₹$monthTotal"
          ))
        }
      case None => // No monthly limit
    }
    
    // 4. Balance check
    if (tx.amount > acc.availableBalance) {
      return Some(createAlert(
        acc.id, 
        AlertType.TransactionBlocked, 
        s"Insufficient balance. Available: ₹${acc.availableBalance}, Required: ₹${tx.amount}"
      ))
    }
    
    None // All checks passed
  }

  /** Attempt to deduct funds and complete transaction */
  private def tryDeduct(acc: Account, tx: Transaction): Either[Alert, (Account, Transaction)] = {
    if (acc.availableBalance < tx.amount) {
      val alert = createAlert(
        acc.id, 
        AlertType.TransactionBlocked, 
        s"Insufficient available balance for ₹${tx.amount}"
      )
      Left(alert)
    } else {
      val completedTx = tx.copy(status = TransactionStatus.Completed)
      val updatedAcc = acc.deductFunds(tx.amount, completedTx)
      Repository.saveAccount(updatedAcc)
      Right((updatedAcc, completedTx))
    }
  }

  /** Sum spending for a specific category in the current month */
  private def sumMonthForCategory(txs: List[Transaction], cat: Category, time: Instant): BigDecimal = {
    val zone = ZonedDateTime.ofInstant(time, ZoneId.systemDefault())
    val year = zone.getYear
    val month = zone.getMonthValue
    
    txs.filter { t =>
      val txZone = ZonedDateTime.ofInstant(t.timestamp, ZoneId.systemDefault())
      txZone.getYear == year && 
      txZone.getMonthValue == month && 
      t.category == cat && 
      t.status == TransactionStatus.Completed
    }.map(_.amount).sum
  }

  /** Sum all spending in the current month */
  private def sumMonthAll(txs: List[Transaction], time: Instant): BigDecimal = {
    val zone = ZonedDateTime.ofInstant(time, ZoneId.systemDefault())
    val year = zone.getYear
    val month = zone.getMonthValue
    
    txs.filter { t =>
      val txZone = ZonedDateTime.ofInstant(t.timestamp, ZoneId.systemDefault())
      txZone.getYear == year && 
      txZone.getMonthValue == month && 
      t.status == TransactionStatus.Completed
    }.map(_.amount).sum
  }

  /** Create an alert without saving (MonitoringService will save it) */
  private def createAlert(accountId: String, alertType: AlertType, message: String): Alert = {
    Alert(
      accountId = accountId,
      alertType = alertType,
      message = message,
      timestamp = Instant.now(),
      riskScore = None,
      requiresAction = false
    )
  }

  /** Validate withdrawal against limits */
  def validateWithdrawal(accountId: String, amount: BigDecimal): Either[Alert, Unit] = {
    Repository.getAccount(accountId) match {
      case None =>
        Left(createAlert(accountId, AlertType.TransactionBlocked, "Account not found"))
        
      case Some(acc) =>
        val limits = acc.limits.withdrawalLimits
        
        // Check daily limit
        limits.daily.foreach { dailyMax =>
          val today = Instant.now()
          val dailyTotal = sumWithdrawalsForDay(acc.transactions, today)
          if (dailyTotal + amount > dailyMax) {
            return Left(createAlert(
              acc.id,
              AlertType.WithdrawalLimitExceeded,
              s"Daily withdrawal limit ₹$dailyMax exceeded. Already withdrawn ₹$dailyTotal today"
            ))
          }
        }
        
        // Check weekly limit
        limits.weekly.foreach { weeklyMax =>
          val weekTotal = sumWithdrawalsForWeek(acc.transactions, Instant.now())
          if (weekTotal + amount > weeklyMax) {
            return Left(createAlert(
              acc.id,
              AlertType.WithdrawalLimitExceeded,
              s"Weekly withdrawal limit ₹$weeklyMax exceeded. Already withdrawn ₹$weekTotal this week"
            ))
          }
        }
        
        // Check monthly limit
        limits.monthly.foreach { monthlyMax =>
          val monthTotal = sumWithdrawalsForMonth(acc.transactions, Instant.now())
          if (monthTotal + amount > monthlyMax) {
            return Left(createAlert(
              acc.id,
              AlertType.WithdrawalLimitExceeded,
              s"Monthly withdrawal limit ₹$monthlyMax exceeded. Already withdrawn ₹$monthTotal this month"
            ))
          }
        }
        
        Right(())
    }
  }

  /** Sum withdrawals for today */
  private def sumWithdrawalsForDay(txs: List[Transaction], time: Instant): BigDecimal = {
    val zone = ZonedDateTime.ofInstant(time, ZoneId.systemDefault())
    val date = zone.toLocalDate
    
    txs.filter { t =>
      val txZone = ZonedDateTime.ofInstant(t.timestamp, ZoneId.systemDefault())
      txZone.toLocalDate == date && 
      t.txType == TransactionType.Withdrawal &&
      t.status == TransactionStatus.Completed
    }.map(_.amount).sum
  }

  /** Sum withdrawals for this week */
  private def sumWithdrawalsForWeek(txs: List[Transaction], time: Instant): BigDecimal = {
    val zone = ZonedDateTime.ofInstant(time, ZoneId.systemDefault())
    val weekStart = zone.toLocalDate.minusDays(zone.getDayOfWeek.getValue - 1)
    
    txs.filter { t =>
      val txZone = ZonedDateTime.ofInstant(t.timestamp, ZoneId.systemDefault())
      !txZone.toLocalDate.isBefore(weekStart) &&
      t.txType == TransactionType.Withdrawal &&
      t.status == TransactionStatus.Completed
    }.map(_.amount).sum
  }

  /** Sum withdrawals for this month */
  private def sumWithdrawalsForMonth(txs: List[Transaction], time: Instant): BigDecimal = {
    val zone = ZonedDateTime.ofInstant(time, ZoneId.systemDefault())
    val year = zone.getYear
    val month = zone.getMonthValue
    
    txs.filter { t =>
      val txZone = ZonedDateTime.ofInstant(t.timestamp, ZoneId.systemDefault())
      txZone.getYear == year && 
      txZone.getMonthValue == month &&
      t.txType == TransactionType.Withdrawal &&
      t.status == TransactionStatus.Completed
    }.map(_.amount).sum
  }
}
