package channels

import java.util.concurrent.atomic.AtomicInteger

import actors.Actors._
import gtp3._
import models._
import play.api.libs.json.{JsNull, JsValue, Json}
import reactive._

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._

object Auth extends ChannelValidator {
	// Keep track of socket for which an authenticated channel has already been opened
	private val already_open = mutable.WeakHashMap[Socket, Boolean]() withDefaultValue false

	def open(request: ChannelRequest) = {
		if (already_open(request.socket)) {
			request.reject(105, "Cannot open more than one auth channel per socket")
		} else {
			already_open.update(request.socket, true)
			request.accept(new Auth)
		}
	}
}

class Auth extends ChannelHandler {
	// Count parallel requests
	private val count = new AtomicInteger(0)
	private val concurrent = Future.failed[Payload](new Exception("Concurrent requests on auth channel are forbidden"))

	// Break the multiplexing feature of GTP3 for the auth channel
	// This prevents running multiple login attempts in parallel
	override def request(req: String, payload: Payload): Future[Payload] = {
		val res =
			if (count.incrementAndGet() != 1) concurrent
			else super.request(req, payload)

		res andThen { case _ => count.decrementAndGet() }
	}

	// Salt used for authentication
	private var salt = utils.randomToken()

	request("auth") { payload =>
		utils.atLeast(500.milliseconds) {
			val res: Future[JsValue] = AuthService.auth(payload.string) map { user =>
				socket.user = user
				Json.toJson(user)
			} recover {
				case _ => JsNull
			}
			res
		}
	}

	request("prepare") { payload =>
		utils.atLeast(500.milliseconds) {
			val user = payload.value.as[String]
			AuthService.setting(user) map { setting => Json.obj("salt" -> salt, "setting" -> setting) }
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
