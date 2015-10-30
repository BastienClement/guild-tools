package channels

import actors.ChatService
import actors.ChatService.{UserAway, UserConnected, UserDisconnected}
import akka.actor.Props
import gtp3._
import models.User
import play.api.libs.json.Json
import reactive.ExecutionContext

object Chat extends ChannelValidator {
	def open(request: ChannelRequest) = request.accept(Props(new Chat(request.user)))
}

class Chat(val user: User) extends ChannelHandler {
	var interests = Set[Int]()

	init {
		ChatService.subscribe(user)
		ChatService.onlines() map { list =>
			for ((user, away) <- list) yield Json.arr(user, away)
		} foreach { onlines =>
			send("onlines", onlines)
		}
	}

	akka {
		case UserConnected(u) => send("connected", u.id)
		case UserAway(u, away) => send("away-changed", (u.id, away))
		case UserDisconnected(u) => send("disconnected", u.id)
	}

	message("set-away") { payload =>
		ChatService.setAway(self, payload.value.as[Boolean])
	}

	message("set-interest") { payload =>
		val room = payload("room").as[Int]
		if (payload("interested").as[Boolean]) interests += room
		else interests -= room
	}

	request("room-backlog") { payload =>
		val room = payload("room").as[Int]
		val count = payload("count").asOpt[Int]
		val limit = payload("limit").asOpt[Int]
		ChatService.roomBacklog(room, user, count, limit)
	}
}
