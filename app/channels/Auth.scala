package channels

import scala.concurrent.Future
import scala.concurrent.duration._
import play.api.libs.json.{JsNull, JsValue, Json}
import actors.Actors._
import gtp3._
import reactive._
import models._

object Auth extends ChannelValidator {
	def open(request: ChannelRequest) = {
		if (request.socket.auth_open) {
			request.reject(105, "Cannot open more than one auth channel per socket")
		} else {
			request.socket.auth_open = true
			request.accept(new Auth)
		}
	}
}

class Auth extends ChannelHandler {
	val handlers = Map[String, Handler](
		"auth" -> auth,
		"prepare" -> prepare,
		"login" -> login
	)

	def init() = {}

	var salt = utils.randomToken()

	def auth(payload: Payload): Future[Payload] = utils.atLeast(500.milliseconds) {
		AuthService.auth(payload.string).map(user => {
			socket.user = user
			Json.toJson(user)
		}).fallbackTo[JsValue](JsNull)
	}

	def prepare(payload: Payload): Future[Payload] = utils.atLeast(500.milliseconds) {
		val user = payload.value.as[String]
		AuthService.setting(user) map { setting => Json.obj("salt" -> salt, "setting" -> setting) }
	}

	def login(payload: Payload): Future[Payload] = utils.atLeast(500.milliseconds) {
		val cur_salt = salt
		salt = utils.randomToken()
		AuthService.login(payload("user").as[String], payload("pass").as[String], cur_salt)
	}
}
