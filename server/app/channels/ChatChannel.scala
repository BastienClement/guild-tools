package channels

import actors.ChatService
import actors.ChatService.{UserAway, UserConnected, UserDisconnected}
import akka.actor.Props
import boopickle.DefaultBasic._
import gtp3._
import models.User
import reactive.ExecutionContext

object ChatChannel extends ChannelValidator {
	def open(request: ChannelRequest) = request.accept(Props(new ChatChannel(request.user)))
}

class ChatChannel(val user: User) extends ChannelHandler {
	var interests = Set[Int]()

	init {
		ChatService.subscribe(user)
		ChatService.onlines().foreach { onlines =>
			send("onlines", onlines)
		}
	}

	akka {
		case UserConnected(u) => send("connected", u.id)
		case UserAway(u, away) => send("away-changed", (u.id, away))
		case UserDisconnected(u) => send("disconnected", u.id)
	}

	message("set-away") { away: Boolean =>
		ChatService.setAway(self, away)
	}

	message("set-interest") { (room: Int, interested: Boolean) =>
		if (interested) interests += room
		else interests -= room
	}

	request("room-backlog") { (room: Int, count: Option[Int], limit: Option[Int]) =>
		ChatService.roomBacklog(room, Some(user), count, limit)
	}
}
