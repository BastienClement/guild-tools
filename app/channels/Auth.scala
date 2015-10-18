package channels

import actors.Actors._
import akka.actor.{ActorRef, Props}
import gtp3.Socket.{Opener, SetUser}
import gtp3._
import java.util.concurrent.atomic.AtomicInteger
import models._
import play.api.libs.json.{JsNull, Json}
import reactive._
import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._

object Auth extends ChannelValidator {
	// Keep track of socket for which an authenticated channel has already been opened
	private val already_open = mutable.WeakHashMap[ActorRef, Boolean]() withDefaultValue false

	def open(request: ChannelRequest) = {
		if (already_open(request.socket)) {
			request.reject(105, "Cannot open more than one auth channel per socket")
		} else {
			already_open.update(request.socket, true)
			request.accept(Props(new Auth(request.socket, request.opener)))
		}
	}

	private val concurrent = Future.failed[Payload](new Exception("Concurrent requests on auth channel are forbidden"))
}

class Auth(val socket: ActorRef, val opener: Opener) extends ChannelHandler {
	// Count parallel requests
	private val count = new AtomicInteger(0)

	// Break the multiplexing feature of GTP3 for the auth channel
	// This prevents running multiple login attempts in parallel
	override def handle_request(req: String, payload: Payload): Future[Payload] = {
		if (count.incrementAndGet() != 1) Auth.concurrent
		else super.handle_request(req, payload)
	} andThen {
		case _ => count.decrementAndGet()
	}

	// Salt used for authentication
	private var salt = utils.randomToken()

	request("auth") { payload =>
		AuthService.auth(payload.string) map {
			user =>
				socket ! SetUser(user)
				Json.toJson(user)
		} recover {
			case e => JsNull
		}
	}

	request("prepare") {
		payload =>
			utils.atLeast(500.milliseconds) {
				val user = payload.value.as[String]
				AuthService.setting(user) map {
					setting => Json.obj("salt" -> salt, "setting" -> setting)
				}
			}
	}

	request("login") {
		payload =>
			utils.atLeast(500.milliseconds) {
				val cur_salt = salt
				salt = utils.randomToken()
				val user = payload("user").as[String]
				val pass = payload("pass").as[String]
				AuthService.login(user, pass, cur_salt, opener.ip, opener.ua)
			}
	}
}
