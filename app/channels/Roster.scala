package channels

import actors.Actors._
import actors.RosterService.{CharDeleted, CharUpdate}
import akka.actor.Props
import gt.Global.ExecutionContext
import gtp3._
import models._
import play.api.libs.json.Json

object Roster extends ChannelValidator {
	def open(request: ChannelRequest) = request.accept(Props(new Roster(request.user)))
}

class Roster(val user: User) extends ChannelHandler {
	init {
		RosterService.subscribe(self, user)
	}

	akka {
		case CharUpdate(char) => send("char-updated", char)
		case CharDeleted(id) => send("char-deleted", id)
	}

	message("request-user") { p =>
		val id = p.value.as[Int]
		for {
			user <- RosterService.user(id)
			chars <- RosterService.chars(id)
		} send("user-data", Json.arr(user, chars))
	}

	message("promote-char") { p => RosterService.promoteChar(p.as[Int], user) }
	message("disable-char") { p => RosterService.disableChar(p.as[Int], user) }
	message("enable-char") { p => RosterService.enableChar(p.as[Int], user) }
	message("remove-char") { p => RosterService.removeChar(p.as[Int], user) }

	request("update-char") { p => RosterService.refreshChar(p.as[Int], user) }
}
