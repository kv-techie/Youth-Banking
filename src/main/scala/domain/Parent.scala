package domain

import java.time.Instant
import java.util.UUID

/** Parent / guardian model */
case class Parent(
  id: String = UUID.randomUUID().toString,
  fullName: String,
  email: Option[String] = None,
  phone: Option[String] = None,
  // linked child accounts (account ids)
  linkedAccounts: List[String] = Nil,
  createdAt: Instant = Instant.now()
) {
  def linkAccount(accountId: String): Parent =
    copy(linkedAccounts = (accountId :: linkedAccounts).distinct)
}
