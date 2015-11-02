package controllers.webtools

import controllers.WebTools
import controllers.WebTools.Deny
import play.api.mvc.Action

trait ApplicationController {
	this: WebTools =>

	def dispatch = UserAction { req =>
		if (req.chars.isEmpty) throw Deny
		req.session.data match {
			case session if session.contains("charter") => Redirect("/wt/application/step2")
			case _ => Redirect("/wt/application/step1")
		}
	}

	def step1 = UserAction { req =>
		if (req.chars.isEmpty) throw Deny
		if (req.getQueryString("validate").isDefined) {
			Redirect("/wt/application/step2").withSession(req.session + ("charter" -> "1"))
		} else {
			Ok(views.html.wt.application.step1.render(req))
		}
	}

	def step2 = UserAction { req =>
		if (req.chars.isEmpty) throw Deny
		Ok(views.html.wt.application.step2.render(req))
	}

	/**
	 * Only outputs the guild charter for inclusion into WordPress
	 */
	def charter = Action {
		Ok(views.html.wt.application.charter.render(false))
	}
}
