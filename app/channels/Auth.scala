package channels

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import play.api.libs.json.Json
import actors.Actors._
import api.{MessageFailure, MessageResults}
import gtp3._
import models._
import models.mysql._

object Auth extends ChannelAcceptor {
	def open(request: ChannelRequest) = request.accept(new Auth(request.socket))
}

class Auth(private val socket: Socket) extends ChannelHandler {
	def handlers = {
		case "auth" => auth _
		case "prepare" => prepare _
		case "login" => login _
	}

	private var salt = utils.randomToken()

	def auth(payload: Payload): Future[Payload] = {
		false
	}

	def prepare(payload: Payload): Future[Payload] = utils.atLeast(250.milliseconds) {
		val user = payload.value.as[String].toLowerCase
		Json.obj("salt" -> salt, "setting" -> AuthService.setting(user))
	}

	def login(payload: Payload): Future[Payload] = utils.atLeast(500.milliseconds) {
		val cur_salt = salt
		salt = utils.randomToken()

		Future.failed(new Exception("Failed"))
	}
}
