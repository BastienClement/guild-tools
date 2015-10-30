package channels

import actors.RosterService
import actors.RosterService.{CharDeleted, CharUpdate}
import akka.actor.Props
import gtp3._
import models._
import reactive.ExecutionContext

object Roster extends ChannelValidator {
	def open(request: ChannelRequest) = request.accept(Props(new Roster(request.user)))
}

class Roster(val user: User) extends ChannelHandler {
	init {
		RosterService.subscribe(self, user)
	}

	akka {
		case CharUpdate(char) => send("char-updated", char)
		case CharDeleted(char) => send("char-deleted", char)
	}

	request("preload-roster") { p =>
		for {
			users <- RosterService.roster_users
			chars <- RosterService.roster_chars.value
		} yield {
			for (user <- users) yield (user, chars.getOrElse(user.id, Seq.empty))
		}
	}

	message("request-user") { p =>
		val id = p.value.as[Int]
		for {
			user <- RosterService.user(id)
			chars <- RosterService.chars(id)
		} send("user-data", (user, chars))
	}

	// Allow promoted user to bypass own-character restrictions
	val update_user = if (user.promoted) None else Some(user)

	request("promote-char") { p => RosterService.promoteChar(p.as[Int], update_user) }
	request("disable-char") { p => RosterService.disableChar(p.as[Int], update_user) }
	request("enable-char") { p => RosterService.enableChar(p.as[Int], update_user) }
	request("remove-char") { p => RosterService.removeChar(p.as[Int], update_user) }
	request("update-char") { p => RosterService.refreshChar(p.as[Int], update_user) }
	request("change-role") { p => RosterService.changeRole(p("char").as[Int], p("role").as[String], update_user) }
}
