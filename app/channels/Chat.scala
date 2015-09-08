package channels

import actors.Actors._
import gtp3._
import play.api.libs.json.{JsNull, Json}

import scala.concurrent.Future
import scala.language.postfixOps

object Chat extends ChannelValidator {
	def open(request: ChannelRequest) = request.accept(new Chat)
}

class Chat extends ChannelHandler with InitHandler with CloseHandler {
	val handlers = Map[String, Handler](
		"set-away" -> setAway,
		"set-interest" -> setInterest,
		"room-backlog" -> roomBacklog
	)

	var interests = Set[Int]()

	def init() = {
		ChatService.connect(channel)

		val onlines = for ((user, away) <- ChatService.onlines) yield Json.arr(user, away)
		channel.send("onlines", onlines)
	}

	def close() = {
		ChatService.disconnect(channel)
	}

	def setAway(payload: Payload): Unit = {
		ChatService.setAway(channel, payload.value.as[Boolean])
	}

	def setInterest(payload: Payload): Unit = {
		val room = payload("room").as[Int]
		if (payload("interested").as[Boolean]) {
			interests += room
		} else {
			interests -= room
		}
	}

	def roomBacklog(payload: Payload): Future[Payload] = {
		val room = payload("room").as[Int]
		val count = payload("count").asOpt[Int]
		val limit = payload("limit").asOpt[Int]

		ImplicitFuturePayload(ChatService.roomBacklog(room, user, count, limit))
	}
}
