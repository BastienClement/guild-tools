package controllers

import anorm.SqlStringInterpolation
import play.api.Play.current
import play.api.cache.Cached
import play.api.db.DB
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.mvc.{Action, Controller, RequestHeader}

object Api extends Controller {
	def catchall(path: String) = Action {
		Ok(Json.obj("error" -> "Undefined API call"))
	}

	def gamedata = Cached((_: RequestHeader) => "api/gamedata", 3600) {
		Action {
			DB.withConnection { implicit c =>
				val realms = SQL"SELECT slug, name, locale FROM gt_realms".list() map { row =>
					row[String]("slug") -> Map("name" -> row[String]("name"), "locale" -> row[String]("locale"))
				}

				val classes = SQL"SELECT id, power, name FROM gt_classes".list() map { row =>
					row[Int]("id").toString -> Map("name" -> row[String]("name"), "power" -> row[String]("power"))
				}

				val races = SQL"SELECT id, side, name FROM gt_races".list() map { row =>
					row[Int]("id").toString -> Map("name" -> row[String]("name"), "side" -> row[String]("side"))
				}

				val realms_map = Map[String, Map[String, String]]() ++ realms
				val classes_map = Map[String, Map[String, String]]() ++ classes
				val races_map = Map[String, Map[String, String]]() ++ races

				Ok(Json.obj(
					"realms" -> realms_map,
					"classes" -> classes_map,
					"races" -> races_map))
			}
		}
	}
}