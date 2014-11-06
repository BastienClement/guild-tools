package utils

import scala.concurrent._
import gt.Global.ExecutionContext
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.ws._

object Bnet {
	private val key = current.configuration.getString("bnet.apiKey") getOrElse ""

	def query(api: String, user_params: (String, String)*): Future[JsValue] = {
		val request = WS.url(s"https://eu.api.battle.net/wow$api")
		val params = user_params :+ ("apikey" -> key)

		for {
			result <- request.withQueryString(params: _*).withHeaders("Accept" -> "application/json").get()
			if result.status == 200
		} yield result.json
	}
}
