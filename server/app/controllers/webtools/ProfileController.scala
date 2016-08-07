package controllers.webtools

import actors.RosterService
import play.api.mvc.Controller
import reactive.ExecutionContext
import scala.concurrent.Future

class ProfileController extends Controller with WtController {
	private val ResolvedUnitFuture = Future.successful(())

	/**
	  * Player characters list
	  */
	def profile = UserAction.async { req =>
		req.queryString.get("action") match {
			case Some(a) =>
				val char = req.queryString.get("char").get.head.toInt
				val action = a.head match {
					case "enable" => RosterService.enableToon(char)
					case "disable" => RosterService.disableToon(char)
					case "set-main" => RosterService.promoteToon(char)
					case "remove" => RosterService.removeToon(char)
					case _ => ResolvedUnitFuture
				}
				for (_ <- action) yield Redirect("/wt/profile")

			case None =>
				Future.successful(Ok(views.html.wt.profile.render(req)))
		}
	}
}
