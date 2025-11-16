package domain

import java.time.Instant
import java.util.UUID

/** Transaction status */
enum TransactionStatus:
  case Pending, Completed, Failed, Reversed, Blocked, RequiresParentApproval

/** Transaction metadata (lightweight map) */
type Meta = Map[String, String]

/** Transaction record */
case class Transaction(
  id: String = UUID.randomUUID().toString,
  fromAccountId: String,
  toPayeeId: Option[String],        // None for cash withdrawals or top-ups
  amount: BigDecimal,
  category: Category,
  timestamp: Instant = Instant.now(),
  status: TransactionStatus = TransactionStatus.Pending,
  meta: Meta = Map.empty,
  // optional purpose tag applied by parent when sending funds for a purpose
  purposeTag: Option[PurposeTag] = None
) {
  def markStatus(s: TransactionStatus): Transaction = copy(status = s)
  def withMeta(k: String, v: String): Transaction = copy(meta = meta.updated(k, v))
}
