package services

import domain._
import java.time.{Instant, LocalTime}

/** Provides pre-configured safety modes and scheduling. For demo we implement three modes.
  * Modes are applied by mutating limits on the account (shallow simulation).
  */
enum SafetyMode:
  case SchoolHoursSafe, MaxSecurity, WeekendExplorer

class SmartModesService(monitor: MonitoringService) {

  def applyMode(accountId: String, mode: SafetyMode): Either[Alert, Account] = {
    Repository.getAccount(accountId) match
      case None => Left(monitor.makeAlert(accountId, AlertType.GenericNotice, "Account missing"))
      case Some(acc) =>
        val updated = mode match
          case SafetyMode.SchoolHoursSafe =>
            val newLimits = acc.limits.copy(perTransaction = Some(BigDecimal(200)), monthly = Some(BigDecimal(2000)))
            acc.copy(limits = newLimits)
          case SafetyMode.MaxSecurity =>
            val newLimits = acc.limits.copy(perTransaction = Some(BigDecimal(100)), monthly = Some(BigDecimal(1000)), withdrawalLimits = acc.limits.withdrawalLimits.copy(daily = Some(BigDecimal(200))))
            acc.copy(limits = newLimits)
          case SafetyMode.WeekendExplorer =>
            val newLimits = acc.limits.copy(perTransaction = Some(BigDecimal(2000)), monthly = Some(BigDecimal(10000)))
            acc.copy(limits = newLimits)
        Repository.saveAccount(updated)
        monitor.makeAlert(accountId, AlertType.GenericNotice, s"Applied mode $mode to account")
        Right(updated)
  }
}
