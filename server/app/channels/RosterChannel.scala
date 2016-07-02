package channels

import actors.RosterService
import actors.RosterService.{CharDeleted, CharUpdated}
import akka.actor.Props
import gtp3._
import models._
import reactive.ExecutionContext

object RosterChannel extends ChannelValidator {
	def open(request: ChannelRequest) = request.accept(Props(new RosterChannel(request.user)))
}

class RosterChannel(val user: User) extends ChannelHandler {
	init {
		RosterService.subscribe(self, user)
	}

	akka {
		case CharUpdated(char) => send("char-updated", char)
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
		val id = p.as[Int]
		for {
			user <- RosterService.user(id)
			chars <- RosterService.chars(id)
		} send("user-data", (user, chars))
	}

	// Allow promoted user to bypass own-character restrictions
	val update_user = if (user.promoted) None else Some(user)

	request("promote-char") { p =>
		val char = p.as[Int]
		RosterService.promoteChar(char, update_user)
	}

	request("disable-char") { p =>
		val char = p.as[Int]
		RosterService.disableChar(char, update_user)
	}

	request("enable-char") { p =>
		val char = p.as[Int]
		RosterService.enableChar(char, update_user)
	}

	request("remove-char") { p =>
		val char = p.as[Int]
		RosterService.removeChar(char, update_user)
	}

	request("update-char") { p =>
		val char = p.as[Int]
		RosterService.refreshChar(char, update_user)
	}

	request("change-role") { p =>
		val char = p("char").as[Int]
		val role = p("role").as[String]
		RosterService.changeRole(char, role, update_user)
	}
}
