package domain

import java.time.Instant

/** Withdrawal limits grouped by granularity */
case class WithdrawalLimits(
  daily: Option[BigDecimal] = None,
  weekly: Option[BigDecimal] = None,
  monthly: Option[BigDecimal] = None
)

/** Spending and transaction limits
  *
  * - perCategory: optional custom limits for categories
  * - perTransaction: the maximum allowed per single transaction
  * - monthly: total allowed spend per calendar month
  * - incomingCreditMultiplier: multiplier used to compute incoming-credit limit when needed
  */
case class Limits(
  monthly: Option[BigDecimal] = None,
  perTransaction: Option[BigDecimal] = None,
  perCategory: Map[Category, BigDecimal] = Map.empty,
  withdrawalLimits: WithdrawalLimits = WithdrawalLimits(),
  incomingCreditMultiplier: Int = 2, // default: (monthly + withdraw) * 2 computed by services
  allowNightAdditions: Boolean = false
) {
  def categoryLimit(cat: Category): Option[BigDecimal] =
    perCategory.get(cat).orElse(monthly)
}
