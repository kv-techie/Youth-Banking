package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._
import services._
import domain._
import JsonFormats._
import scala.concurrent.{ExecutionContext, Future}
import java.time.LocalTime

@Singleton
class PayeeController @Inject()(
    cc: ControllerComponents,
    payeeService: PayeeSafetyService
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  def addPayee(accountId: String) = Action(parse.json).async { request =>
    request.body.validate[PayeeRequest].fold(
      errors => Future.successful(BadRequest(Json.obj("error" -> JsError.toJson(errors)))),
      req =>
        payeeService.addPayee(accountId, req.displayName, req.accountNumber, LocalTime.now()) match
          case Left(alert) => Future.successful(BadRequest(Json.obj("error" -> alert.message)))
          case Right(payee) => Future.successful(Ok(Json.toJson(payee)))
    )
  }
}
