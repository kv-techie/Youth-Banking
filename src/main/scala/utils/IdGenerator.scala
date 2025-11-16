package utils

import java.util.UUID

/** Simple ID generator for accounts, payees, transactions */
object IdGenerator {

  def generateId(prefix: String = "ID"): String =
    s"$prefix-${UUID.randomUUID().toString.take(8)}"

  def generateAccountId(): String = generateId("ACC")
  def generatePayeeId(): String = generateId("PAY")
  def generateTransactionId(): String = generateId("TX")
}
