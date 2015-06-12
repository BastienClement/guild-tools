package controllers

import scala.util.Try
import models._
import models.simple._
import models.sql._
import play.api.Play.current
import play.api.cache.Cached
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc.{Action, Controller, RequestHeader}
import utils.SmartTimestamp

object Api extends Controller {
	def catchall(path: String) = Action {
		NotFound(Json.obj("error" -> "Undefined API call"))
	}

	def gamedata = Cached((_: RequestHeader) => "api/gamedata", 3600) {
		Action {
			DB.withSession { implicit s =>
				def list_to_map[T](list: List[(T, String, String)], k2: String, k3: String): Map[String, Map[String, String]] = {
					val assoc = list map {
						case (v1, v2, v3) =>
							v1.toString -> Map(k2 -> v2, k3 -> v3)
					}

					assoc.toMap
				}

				val realms_list = sql.queryNA[(String, String, String)]("SELECT slug, name, locale FROM gt_realms").list
				val classes_list = sql.queryNA[(Int, String, String)]("SELECT id, name, power FROM gt_classes").list
				val races_list = sql.queryNA[(Int, String, String)]("SELECT id, name, side FROM gt_races").list

				Ok(Json.obj(
					"realms" -> list_to_map(realms_list, "name", "locale"),
					"classes" -> list_to_map(classes_list, "name", "power"),
					"races" -> list_to_map(races_list, "name", "side")))
			}
		}
	}

	def bugsack = Action(parse.json) { request =>
		val report = request.body

		val user = (report \ "user").asOpt[Int] getOrElse 0
		val rev = (report \ "rev").as[String]
		val error = (report \ "msg").as[String]
		val stack = (report \ "stack").asOpt[String] getOrElse ""
		val navigator = (report \ "nav").asOpt[String] getOrElse ""

		val key = (user, error, stack)
		val bug = BugReport(utils.md5(key.toString()), user, SmartTimestamp.now, rev, error, stack, navigator)

		Try {
			DB.withSession { implicit s => BugSack.insert(bug) }
		}

		NoContent
	}

	def socketURL = Cached((_: RequestHeader) => "api/socket_url", 3600) {
		Action {
			Ok(current.configuration.getString("socket.url") getOrElse "")
		}
	}
}
