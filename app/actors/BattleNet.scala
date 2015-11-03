package actors

import actors.BattleNet._
import models._
import play.api.Play._
import play.api.libs.json.JsValue
import play.api.libs.ws.{WS, WSResponse}
import reactive.ExecutionContext
import scala.concurrent.Future

private[actors] class BattleNetImpl extends BattleNet

object BattleNet extends StaticActor[BattleNet, BattleNetImpl]("BattleNet") {
	/** The Battle.net API key */
	private val key = current.configuration.getString("bnet.apiKey") getOrElse ""

	/**
	  * A Battle.net request failure.
	  * In addition to the `reason` message provided by Battle.net, the full WSResponse object is provided.
	  */
	case class BnetFailure(response: WSResponse) extends Exception((response.json \ "reason").asOpt[String].getOrElse("Unknown reason"))
}

/**
  * Battle.net API client.
  */
trait BattleNet {
	/**
	  * Executes a Battle.net query.
	  * The URl is automatically prefixed with `https://eu.api.battle.net/wow`.
	  * @param api           the API to query
	  * @param user_params   any additional user-provided GET parameters
	  * @return              the JsValue returned by Battle.net
	  */
	def query(api: String, user_params: (String, String)*): Future[JsValue] = {
		val params = user_params :+ ("apikey" -> key)
		val request = WS.url(s"https://eu.api.battle.net/wow$api").withQueryString(params: _*).withRequestTimeout(10000)

		request.withHeaders("Accept" -> "application/json").get() map {
			case res if res.status == 200 => res.json
			case res => throw BnetFailure(res)
		}
	}

	/**
	  * Fetches a character from Battle.net.
	  * @param server    the server name slug
	  * @param name      the character name
	  * @return          a Char corresponding to the fetched character
	  */
	def fetchChar(server: String, name: String): Future[Char] = {
		query(s"/character/$server/$name", "fields" -> "items,talents") map { char =>
			// Extract talents
			val talents = char \ "talents"

			// Get primary role
			val role = (talents(0) :: talents(1) :: Nil) find { tree =>
				(tree \ "selected").asOpt[Boolean] getOrElse false
			} flatMap { tree =>
				(tree \ "spec" \ "role").asOpt[String]
			} getOrElse {
				"DPS"
			}

			Char(
				id = 0,
				name = (char \ "name").as[String],
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
				ilvl = (char \ "items" \ "averageItemLevelEquipped").as[Int],
				role = role)
		}
	}
}
