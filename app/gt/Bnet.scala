package gt

import play.api.Play.current
import play.api.libs.json._
import play.api.libs.ws._

import scala.concurrent._
import scala.concurrent.duration._

object Bnet {
	val key = "p4gwqr2nzwzd6vvseybp422k6aken7e4"

	def query(api: String, params: (String, String)*): Option[JsValue] = {
		val request = WS.url(s"https://eu.api.battle.net/wow$api")
		val full_params = params :+ ("apikey" -> key)

		val f_result = request
				.withQueryString(full_params: _*)
				.withHeaders("Accept" -> "application/json")
				.get()

		val result = Await.result(f_result, 10.seconds)

		if (result.status == 200) {
			Some(result.json)
		} else {
			None
		}
	}
}
