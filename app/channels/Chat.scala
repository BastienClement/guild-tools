package channels

import actors.Actors._
import gtp3._
import play.api.libs.json.Json

import scala.language.postfixOps

object Chat extends ChannelValidator {
	def open(request: ChannelRequest) = request.accept(new Chat)
}

class Chat extends ChannelHandler with InitHandler with CloseHandler {
	var interests = Set[Int]()

	def init() = {
		ChatService.connect(channel)

		val onlines = for ((user, away) <- ChatService.onlines) yield Json.arr(user, away)
		channel.send("onlines", onlines)
	}

	def close() = {
		ChatService.disconnect(channel)
	}

	message("set-away") { payload =>
		ChatService.setAway(channel, payload.value.as[Boolean])
	}

	message("set-interest") { payload =>
		val room = payload("room").as[Int]
		if (payload("interested").as[Boolean]) {
			interests += room
		} else {
			interests -= room
		}
	}

	request("room-backlog") { payload =>
		val room = payload("room").as[Int]
		val count = payload("count").asOpt[Int]
		val limit = payload("limit").asOpt[Int]

		ChatService.roomBacklog(room, user, count, limit)
	}
}
