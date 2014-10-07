package controllers

import models._
import models.sql._
import play.api.Play.current
import play.api.cache.Cached
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc.{Action, Controller, RequestHeader}

object Api extends Controller {
	def catchall(path: String) = Action {
		NotFound(Json.obj("error" -> "Undefined API call"))
	}

	def gamedata = Cached((_: RequestHeader) => "api/gamedata", 3600) {
		Action {
			DB.withSession { implicit s =>
				def list_to_map[T](list: List[(T, String, String)], k2: String, k3: String): Map[String, Map[String, String]] = {
					val assoc = list map { case (v1, v2, v3) =>
						v1.toString -> Map(k2 -> v2, k3 -> v3)
					}

					assoc.toMap
				}

				val realms_list = sql"SELECT slug, name, locale FROM gt_realms".as[(String, String, String)].list
				val classes_list = sql"SELECT id, name, power FROM gt_classes".as[(Int, String, String)].list
				val races_list = sql"SELECT id, name, side FROM gt_races".as[(Int, String, String)].list

				Ok(Json.obj(
					"realms" -> list_to_map(realms_list, "name", "locale"),
					"classes" -> list_to_map(classes_list, "name", "power"),
					"races" -> list_to_map(races_list, "name", "side")))
			}
		}
	}
}
