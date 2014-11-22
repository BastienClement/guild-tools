package actors

import scala.concurrent.Future
import gt.Global.ExecutionContext
import models._
import play.api.Play._
import play.api.libs.json.JsValue
import play.api.libs.ws.WS

/**
 * Public interface
 */
trait BattleNet {
	def query(api: String, user_params: (String, String)*): Future[JsValue]
	def fetchChar(server: String, name: String): Future[Char]
}

/**
 * Actor implementation
 */
class BattleNetImpl extends BattleNet {
	private val key = current.configuration.getString("bnet.apiKey") getOrElse ""

	def query(api: String, user_params: (String, String)*): Future[JsValue] = {
		val params = user_params :+ ("apikey" -> key)
		val request = WS.url(s"https://eu.api.battle.net/wow$api").withQueryString(params: _*)

		request.withHeaders("Accept" -> "application/json").get() filter {
			_.status == 200
		} map {
			_.json
		}
	}

	def fetchChar(server: String, name: String): Future[Char] = {
		query(s"/character/$server/$name", "fields" -> "items,talents") map { char =>
			val talents = char \ "talents"

			val role = (talents(0) :: talents(1) :: Nil) find { tree =>
				(tree \ "selected").asOpt[Boolean] getOrElse false
			} flatMap { tree =>
				(tree \ "spec" \ "role").asOpt[String]
			} getOrElse {
				"DPS"
			}

			Char(
				id = 0,
				name = name,
				server = server,
				owner = 0,
				main = false,
				active = true,
				`class` = (char \ "class").as[Int],
				race = (char \ "race").as[Int],
				gender = (char \ "gender").as[Int],
				level = (char \ "level").as[Int],
				achievements = (char \ "achievementPoints").as[Int],
				thumbnail = (char \ "thumbnail").as[String],
				ilvl = (char \ "items" \ "averageItemLevel").as[Int],
				role = role)
		}
	}
}