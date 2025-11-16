package utils

import domain.{Transaction, Account, Payee}

/** Simple AI-driven risk engine stub */
object RiskEngine {

  /** Returns true if a transaction seems risky */
  def isRiskyTransaction(tx: Transaction, account: Account, payee: Option[Payee]): Boolean = {
    // 1. Night-time unknown payee
    val nightRisk = TimeUtils.isNightTime() && payee.exists(p => !account.trustedPayees.contains(p.id))

    // 2. Large transaction beyond usual
    val largeTxRisk = tx.amount > account.dailyAverageTransaction * 5

    // 3. Unknown categories flagged
    val categoryRisk = tx.category.name.toLowerCase match
      case "gambling" | "crypto" => true
      case _ => false

    nightRisk || largeTxRisk || categoryRisk
  }

  /** Returns risk level: LOW, MEDIUM, HIGH */
  def riskLevel(tx: Transaction, account: Account, payee: Option[Payee]): String = {
    if (isRiskyTransaction(tx, account, payee)) "HIGH"
    else if (tx.amount > account.dailyAverageTransaction * 2) "MEDIUM"
    else "LOW"
  }
}
