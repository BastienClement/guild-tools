package controllers.webtools

import actors.RosterService
import controllers.WebTools
import models._
import models.mysql._
import reactive.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import utils.CacheCell

object ProfileController {
	private val ResolvedUnitFuture = Future.successful(())
}

trait ProfileController {
	this: WebTools =>
	import ProfileController._

	def profile = UserAction.async { req =>
		val user = req.user
		val get = req.queryString

		get.get("action") match {
			case Some(a) =>
				val char = get("char").head.toInt
				val action = a.head match {
					case "enable" => RosterService.enableChar(char)
					case "disable" => RosterService.disableChar(char)
					case "set-main" => RosterService.promoteChar(char)
					case "remove" => RosterService.removeChar(char)
					case _ => ResolvedUnitFuture
				}
				for (_ <- action) yield Redirect("/wt/profile")

			case None =>
				Future.successful(Ok(views.html.wt.profile.render(req)))
		}
	}
}
