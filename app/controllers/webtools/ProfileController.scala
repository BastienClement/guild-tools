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
	private val server_names = CacheCell.async[Map[String, String]](10.minutes) {
		val query = sql"""SELECT slug, name FROM gt_realms""".as[(String, String)]
		DB.run(query).map(_.toMap)
	}

	private val class_names = CacheCell.async[Map[Int, String]](10.minutes) {
		val query = sql"""SELECT id, name FROM gt_classes""".as[(Int, String)]
		DB.run(query).map(_.toMap)
	}

	private val races_names = CacheCell.async[Map[Int, String]](10.minutes) {
		val query = sql"""SELECT id, name FROM gt_races""".as[(Int, String)]
		DB.run(query).map(_.toMap)
	}

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
				for {
					sn <- server_names.value
					cn <- class_names.value
					rn <- races_names.value
				} yield {
					Ok(views.html.wt.profile.render(sn, cn, rn, req))
				}
		}
	}
}
