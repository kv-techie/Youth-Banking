package controllers

import play.api.libs.json._
import domain.*

object JsonFormats {
  implicit val categoryFormat: Format[Category] = Json.format[Category]
  implicit val purposeTagFormat: Format[PurposeTag] = Json.format[PurposeTag]
  implicit val transactionStatusFormat: Format[TransactionStatus] = Json.format[TransactionStatus]
  implicit val transactionFormat: Format[Transaction] = Json.format[Transaction]
  implicit val payeeFormat: Format[Payee] = Json.format[Payee]
  implicit val accountFormat: Format[Account] = Json.format[Account]
  implicit val alertFormat: Format[Alert] = Json.format[Alert]

  // request DTOs
  case class TransactionRequest(payeeId: String, amount: BigDecimal, category: Category, purposeTag: Option[PurposeTag])
  implicit val transactionRequestFormat: Format[TransactionRequest] = Json.format[TransactionRequest]

  case class IncomingCreditRequest(amount: BigDecimal, sender: String)
  implicit val incomingCreditRequestFormat: Format[IncomingCreditRequest] = Json.format[IncomingCreditRequest]

  case class PayeeRequest(displayName: String, accountNumber: String)
  implicit val payeeRequestFormat: Format[PayeeRequest] = Json.format[PayeeRequest]
}
