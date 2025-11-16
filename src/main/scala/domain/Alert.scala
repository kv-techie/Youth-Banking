package domain

import java.time.Instant
import java.util.UUID

/** Types of alerts the system can generate */
enum AlertType:
  case LargeTransaction, IncomingCreditExceeded, NewPayeeAdded, RepeatedPaymentsToUnknownPayee,
       NightTimeActivity, WithdrawalExceeded, FraudSuspected, PurposeUnlockRequested,
       EmergencyOverrideUsed, GenericNotice

/** Alert model */
case class Alert(
  id: String = UUID.randomUUID().toString,
  accountId: String,
  alertType: AlertType,
  message: String,
  createdAt: Instant = Instant.now(),
  seen: Boolean = false,
  metadata: Map[String, String] = Map.empty
) {
  def markSeen: Alert = copy(seen = true)
}
