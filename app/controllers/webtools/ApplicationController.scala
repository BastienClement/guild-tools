package controllers.webtools

import controllers.WebTools
import play.api.mvc.Action
import scala.concurrent.Future

trait ApplicationController {
	this: WebTools =>

	def apply = UserAction.async { req =>
		Future.successful(Ok(views.html.wt.application.render(req.user)))
	}

	def charter = Action {
		Ok(views.html.wt.application_charter.render())
	}
}
