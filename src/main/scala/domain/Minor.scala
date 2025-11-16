package domain

import java.time.{Instant, LocalDate}
import java.util.UUID

/** Minor / child model */
case class Minor(
  id: String = UUID.randomUUID().toString,
  fullName: String,
  dateOfBirth: LocalDate,
  parentIds: List[String] = Nil,
  accountId: Option[String] = None,
  createdAt: Instant = Instant.now()
) {
  def isAdult(reference: java.time.LocalDate = java.time.LocalDate.now): Boolean =
    java.time.Period.between(dateOfBirth, reference).getYears >= 18

  def attachAccount(accountId: String): Minor =
    copy(accountId = Some(accountId))
}
