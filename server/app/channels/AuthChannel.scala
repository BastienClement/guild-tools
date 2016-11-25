package channels

import akka.actor.{ActorRef, Props}
import boopickle.DefaultBasic._
import gt.GuildTools
import gtp3.Socket.{Opener, SetUser}
import gtp3._
import java.util.concurrent.atomic.AtomicInteger
import models.User
import reactive._
import scala.collection.mutable
import scala.concurrent.Future
import scala.util.Try

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

	def authorized(user: User) = user.fs

	request("auth") { session: String =>
		GuildTools.ws.url("https://auth.fromscratch.gg/oauth/tokeninfo").withQueryString("token" -> session).get().map { response =>
			Try {
				val user = response.json \ "user"
				Some(User((user \ "id").as[Int], (user \ "name").as[String], (user \ "group").as[Int]))
			}.getOrElse(None)
		}.map {
			case Some(user) if authorized(user) =>
				socket ! SetUser(user, session)
				user

			case Some(_) => throw new Exception("Unauthorized")
			case None => null
		}
	}
}
