package channels

import java.util.concurrent.atomic.AtomicInteger

import actors.Actors._
import akka.actor.{ActorRef, Props}
import channels.Auth.SetUser
import gtp3._
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
			request.accept(Props(new Auth(request.socket)))
		}
	}

	private val concurrent = Future.failed[Payload](new Exception("Concurrent requests on auth channel are forbidden"))

	// Message sent to the Socket actor to define the authenticated user for this socket
	case class SetUser(user: User)
}

class Auth(val socket: ActorRef) extends ChannelHandler {
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
		utils.atLeast(500.milliseconds) {
			AuthService.auth(payload.string) map { user =>
				socket ! SetUser(user)
				Json.toJson(user)
			} recover {
				case e => JsNull
			}
		}
	}

		request("prepare") { payload =>
			utils.atLeast(500.milliseconds) {
				val user = payload.value.as[String]
				AuthService.setting(user) map {
					setting => Json.obj("salt" -> salt, "setting" -> setting)
				}
			}
	}

	request("login") { payload =>
		utils.atLeast(500.milliseconds) {
			val cur_salt = salt
			salt = utils.randomToken()
			AuthService.login(payload("user").as[String], payload("pass").as[String], cur_salt)
		}
	}
}
