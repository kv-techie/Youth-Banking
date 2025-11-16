package domain

import java.time.Instant
import java.util.UUID

/** Payee model — merchant or personal payee */
case class Payee(
  id: String = UUID.randomUUID().toString,
  displayName: String,
  accountNumber: String,    // tokenized / masked in a real system
  ifscOrRouting: Option[String] = None,
  trusted: Boolean = false,
  addedAt: Instant = Instant.now(),
  // optionally, a merchant category if recognized/verified
  merchantCategory: Option[Category] = None
) {
  def markTrusted: Payee = copy(trusted = true)
}
