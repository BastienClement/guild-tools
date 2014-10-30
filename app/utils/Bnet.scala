package utils

import scala.concurrent._
import gt.Global.ExecutionContext
import play.api.Play
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.ws._

object Bnet {
	private val key = Play.current.configuration.getString("bnet.apiKey")

	def query(api: String, user_params: (String, String)*): Future[Option[JsValue]] = {
		val request = WS.url(s"https://eu.api.battle.net/wow$api")
		val params = user_params :+ ("apikey" -> key)

		val res = request.withQueryString(params: _*).withHeaders("Accept" -> "application/json").get

		res map { result =>
			if (result.status == 200) {
				Some(result.json)
			} else {
				None
			}
		}
	}
}
