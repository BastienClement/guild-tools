package controllers

import models._
import models.sql._
import play.api.Play.current
import play.api.cache.Cached
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc.{ Action, Controller, RequestHeader }
import scala.slick.jdbc.StaticQuery

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
}
