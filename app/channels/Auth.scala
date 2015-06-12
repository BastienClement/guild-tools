package channels

import scala.concurrent.Future
import scala.concurrent.duration._
import play.api.libs.json.Json
import actors.Actors._
import gtp3._
import reactive._

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
		AuthService.setting(user) map { setting => Json.obj("salt" -> salt, "setting" -> setting) }
	}

	def login(payload: Payload): Future[Payload] = utils.atLeast(500.milliseconds) {
		val cur_salt = salt
		salt = utils.randomToken()

		Future.failed(new Exception("Failed"))
	}
}
