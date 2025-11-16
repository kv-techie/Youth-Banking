package domain

import java.time.Instant
import java.util.UUID

/** Represents a minor's bank account.
  *
  * This file intentionally keeps logic minimal (pure helpers only).
  */
case class Account(
  id: String = UUID.randomUUID().toString,
  minorId: String,
  balance: BigDecimal = BigDecimal(0),
  // Locked funds keyed by PurposeTag: visible but unusable until unlocked by parent
  lockedFunds: Map[PurposeTag, BigDecimal] = Map.empty,
  limits: Limits,
  payees: List[Payee] = Nil,
  transactions: List[Transaction] = Nil,
  createdAt: Instant = Instant.now()
) {

  /** Available (usable) balance excluding locked funds. */
  def availableBalance: BigDecimal =
    balance - lockedFunds.values.sum

  /** Total funds (including locked). */
  def totalBalance: BigDecimal = balance

  /** Add funds (e.g., incoming credit). Returns updated Account. */
  def addFunds(amount: BigDecimal): Account =
    copy(balance = balance + amount)

  /** Deduct funds (if sufficient available balance) and add transaction to ledger.
    * Note: This method is pure — it does not validate limits; validation belongs to services.
    */
  def deductFunds(amount: BigDecimal, tx: Transaction): Account =
    copy(balance = balance - amount, transactions = tx :: transactions)

  /** Lock a portion of funds to a purpose. */
  def lockFunds(purpose: PurposeTag, amount: BigDecimal): Account = {
    val prev = lockedFunds.getOrElse(purpose, BigDecimal(0))
    copy(lockedFunds = lockedFunds.updated(purpose, prev + amount))
  }

  /** Unlock funds for a purpose (reduce locked amount). */
  def unlockFunds(purpose: PurposeTag, amount: BigDecimal): Account = {
    val prev = lockedFunds.getOrElse(purpose, BigDecimal(0))
    val next = (prev - amount) max BigDecimal(0)
    if (next == BigDecimal(0)) copy(lockedFunds = lockedFunds - purpose)
    else copy(lockedFunds = lockedFunds.updated(purpose, next))
  }

  /** Mark a payee trusted (pure update). */
  def markPayeeTrusted(payeeId: String): Account =
    copy(payees = payees.map(p => if (p.id == payeeId) p.copy(trusted = true) else p))

  /** Add or update a payee (if id exists it replaces). */
  def upsertPayee(payee: Payee): Account = {
    val others = payees.filterNot(_.id == payee.id)
    copy(payees = payee :: others)
  }
}
