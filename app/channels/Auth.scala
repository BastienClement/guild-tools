package channels

import scala.concurrent.Future
import scala.concurrent.duration._
import play.api.libs.json.{JsNull, JsValue, Json}
import actors.Actors._
import gtp3._
import reactive._
import models._

object Auth extends ChannelAcceptor {
	def open(request: ChannelRequest) = request.accept(new Auth)
}

class Auth extends ChannelHandler {
	def handlers = {
		case "auth" => auth _
		case "prepare" => prepare _
		case "login" => login _
	}

	var salt = utils.randomToken()

	def auth(payload: Payload): Future[Payload] = {
		AuthService.auth(payload.string).map(user => {
			socket.user = user
			Json.toJson(user)
		}).fallbackTo[JsValue](JsNull)
	}

	def prepare(payload: Payload): Future[Payload] = utils.atLeast(250.milliseconds) {
		val user = payload.value.as[String]
		AuthService.setting(user) map { setting => Json.obj("salt" -> salt, "setting" -> setting) }
	}

	def login(payload: Payload): Future[Payload] = utils.atLeast(500.milliseconds) {
		val cur_salt = salt
		salt = utils.randomToken()
		AuthService.login(payload.get("user").as[String], payload.get("pass").as[String], cur_salt)
	}
}
