package controllers.webtools

import controllers.WebTools
import controllers.WebTools.{Deny, UserRequest}
import models._
import models.application.{Application, Applications, Stage}
import models.mysql._
import play.api.mvc.{Action, AnyContent, Result}
import reactive.ExecutionContext
import scala.concurrent.Future

trait ApplicationController {
	this: WebTools =>

	private def ApplicationActionIgnore(ignore: Boolean)(action: (UserRequest[AnyContent], Application) => Future[Result]): Action[AnyContent] = {
		val FutureRedirect = Future.successful(Redirect("/wt/application"))
		UserAction.async { req =>
			if (req.chars.isEmpty) throw Deny
			Applications.lastForUser(req.user.id).result.headOption.run flatMap {
				case Some(a) => action(req, a)
				case None if ignore => action(req, null)
				case _ => FutureRedirect
			}
		}
	}

	private def ApplicationActionAsync(action: (UserRequest[AnyContent], Application) => Future[Result]): Action[AnyContent] = {
		ApplicationActionIgnore(false)(action)
	}

	private def ApplicationAction(action: (UserRequest[AnyContent], Application) => Result): Action[AnyContent] = {
		ApplicationActionIgnore(false) { case (r, a) => Future.successful(action(r, a)) }
	}

	/**
	  * Dispatches the user to the correct stage page.
	  */
	def application_dispatch = ApplicationActionIgnore(true) { case (req, application) =>
		Future.successful {
			Redirect {
				(if (req.session.data.contains("ignore")) null else application) match {
					case _ if req.user.member => "/wt/application/member"

					case null if req.session.data.contains("charter") => "/wt/application/step2"
					case null => "/wt/application/step1"

					case a if a.stage > Stage.Trial.id => "/wt/application/step6"
					case a if a.stage == Stage.Trial.id => "/wt/application/step5"
					case a if a.stage == Stage.Review.id => "/wt/application/step4"
					case a if a.stage == Stage.Pending.id => "/wt/application/step3"
				}
			}
		}
	}

	/**
	  * Guild charter stage
	  */
	def application_step1 = UserAction { req =>
		if (req.chars.isEmpty) throw Deny
		if (req.getQueryString("validate").isDefined) {
			Redirect("/wt/application/step2").withSession(req.session + ("charter" -> "1"))
		} else {
			Ok(views.html.wt.application.step1.render(req))
		}
	}

	/**
	  * Application form
	  */
	def application_step2 = UserAction { req =>
		if (req.chars.isEmpty) throw Deny
		req.session.data.contains("charter") match {
			case true => Ok(views.html.wt.application.step2.render(req))
			case false => Redirect("/wt/application/step1")
		}
	}

	/**
	  * Application is in Pending stage.
	  */
	def application_step3 = ApplicationAction {
		case (_, application) if application == null || application.stage != Stage.Pending.id => Redirect("/wt/application")
		case (req, _) => Ok(views.html.wt.application.step4.render(req))
	}

	/**
	  * Applicantion is in Review stage.
	  */
	def application_step4 = ApplicationAction {
		case (_, application) if application == null || application.stage != Stage.Review.id => Redirect("/wt/application")
		case (req, _) => Ok(views.html.wt.application.step4.render(req))
	}

	/**
	  * Applicant is in trial.
	  */
	def application_step5 = ApplicationAction {
		case (_, application) if application == null || application.stage != Stage.Trial.id => Redirect("/wt/application")
		case (req, _) => Ok(views.html.wt.application.step5.render(req))
	}

	/**
	  * Archived application.
	  * Used for Refused, Accepted and Archived
	  */
	def application_step6 = ApplicationAction {
		case (_, application) if application == null || application.stage < Stage.Refused.id => Redirect("/wt/application")
		case (req, _) => Ok(views.html.wt.application.step6.render(req))
	}

	/**
	  * User is a guild member. Display placeholder page.
	  */
	def application_member = UserAction { req => Ok(views.html.wt.application.member.render(req)) }

	/**
	  * Only outputs the guild charter for inclusion into WordPress.
	  */
	def application_charter = Action {Ok(views.html.wt.application.charter.render(false))}
}
