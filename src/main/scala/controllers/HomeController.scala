package controllers

import play.api.mvc._
import javax.inject._

@Singleton
class HomeController @Inject()(val controllerComponents: ControllerComponents) 
  extends BaseController {
  
  def health = Action {
    Ok("Youth Banking API is running")
  }
}
