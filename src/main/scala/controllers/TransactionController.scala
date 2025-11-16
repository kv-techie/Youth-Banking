package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._
import services._
import domain._
import JsonFormats._
import scala.concurrent.{ExecutionContext, Future}
import java.time.Instant

@Singleton
class TransactionController @Inject()(
    cc: ControllerComponents,
    spendingService: SpendingControlService,
    incomingCreditService: IncomingCreditService,
    unknownPayeeService: UnknownPayeeTransferService,
    monitoringService: MonitoringService,
    purposeOverrideService: PurposeOverrideService,
    emergencyService: EmergencyOverrideService
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  def performTransaction(accountId: String) = Action(parse.json).async { request =>
    request.body.validate[TransactionRequest].fold(
      errors => Future.successful(BadRequest(Json.obj("error" -> JsError.toJson(errors)))),
      txReq =>
        Repository.getAccount(accountId) match
          case None => Future.successful(NotFound(Json.obj("error" -> "Account not found")))
          case Some(account) =>
            val payeeOpt = account.payees.find(_.id == txReq.payeeId)
            payeeOpt match
              case None => Future.successful(NotFound(Json.obj("error" -> "Payee not found")))
              case Some(payee) =>
                Future.successful(
                  purposeOverrideService.tryConsumePurposeFunds(account.id,
                    Transaction(account.id, Some(payee.id), txReq.amount, txReq.category, Instant.now(), TransactionStatus.Pending, txReq.purposeTag),
                    Some(txReq.category))
                    .fold(err => BadRequest(Json.obj("error" -> err.message)), { case (updated, tx) => Ok(Json.toJson(updated)) })
                )
    )
  }

  def incomingCredit(accountId: String) = Action(parse.json).async { request =>
    request.body.validate[IncomingCreditRequest].fold(
      errors => Future.successful(BadRequest(Json.obj("error" -> JsError.toJson(errors)))),
      req =>
        Repository.getAccount(accountId) match
          case None => Future.successful(NotFound(Json.obj("error" -> "Account not found")))
          case Some(acc) =>
            val updated = incomingCreditService.processIncoming(accountId, req.amount)
            Future.successful(Ok(Json.toJson(updated)))
    )
  }

}
