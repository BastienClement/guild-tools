package channels

import java.util.concurrent.atomic.AtomicInteger

import actors._
import akka.actor.{ActorRef, Props}
import gtp3.Socket.{Opener, SetUser}
import gtp3._
import models._
import play.api.libs.json.{JsNull, Json}
import reactive._

import scala.collection.mutable
import scala.concurrent.Future

object AuthChannel extends ChannelValidator {
	/** Keep track of socket for which an authenticated channel has already been opened */
	private val already_open = mutable.WeakHashMap[ActorRef, Boolean]() withDefaultValue false

	def open(request: ChannelRequest) = {
		if (already_open(request.socket)) {
			request.reject(105, "Cannot open more than one auth channel per socket")
		} else {
			already_open.update(request.socket, true)
			request.accept(Props(new AuthChannel(request.socket, request.opener)))
		}
	}

	/** Cache of a failed future for concurrent requests */
	private val concurrent = Future.failed[Payload](new Exception("Concurrent requests on auth channel are forbidden"))
}

class AuthChannel(val socket: ActorRef, val opener: Opener) extends ChannelHandler {
	// Count parallel requests
	private val count = new AtomicInteger(0)

	// Break the multiplexing feature of GTP3 for the auth channel
	// This prevents running multiple login attempts in parallel
	override def handle_request(req: String, payload: Payload): Future[Payload] = {
		if (count.incrementAndGet() != 1) AuthChannel.concurrent
		else super.handle_request(req, payload)
	} andThen {
		case _ => count.decrementAndGet()
	}

	def authorized(user: User) = AuthService.allowed_groups.contains(user.group)

	request("auth") { payload =>
		AuthService.auth(payload.string).map { user =>
			socket ! SetUser(user)
			(Json.toJson(user), Some(user))
		}.recover {
			case e => (JsNull, None)
		}.map {
			case (json, Some(user)) if !authorized(user) => throw new Exception("Not authorized")
			case (json, _) => json
		}
	}
}
