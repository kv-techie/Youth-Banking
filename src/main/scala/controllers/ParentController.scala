package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._
import services._
import domain._
import JsonFormats._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ParentController @Inject()(
    cc: ControllerComponents,
    purposeOverrideService: PurposeOverrideService
)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  def tagFunds(accountId: String, purpose: String, amount: BigDecimal, parentId: String) = Action.async {
    Repository.getAccount(accountId) match
      case None => Future.successful(NotFound(Json.obj("error" -> "Account not found")))
      case Some(acc) =>
        val tag = PurposeTag.withNameOption(purpose) match
          case Some(t) => t
          case None => return Future.successful(BadRequest(Json.obj("error" -> s"Invalid purpose $purpose")))

        purposeOverrideService.tagFundsForPurpose(accountId, tag, amount, parentId) match
          case Left(alert) => Future.successful(BadRequest(Json.obj("error" -> alert.message)))
          case Right(updated) => Future.successful(Ok(Json.toJson(updated)))
  }
}
