package channels

import actors.RosterService
import actors.RosterService.{ToonDeleted, ToonUpdated}
import akka.actor.Props
import api.Roster.UserData
import boopickle.DefaultBasic._
import gtp3._
import model.User
import scala.concurrent.ExecutionContext.Implicits.global

object RosterChannel extends ChannelValidator {
	def open(request: ChannelRequest) = request.accept(Props(new RosterChannel(request.user)))
}

class RosterChannel(val user: User) extends ChannelHandler {
	init {
		RosterService.subscribe(self, user)
	}

	akka {
		case ToonUpdated(toon) => send("toon-updated", toon)
		case ToonDeleted(toon) => send("toon-deleted", toon)
	}

	request("load-roster") {
		for {
			users <- RosterService.roster_users
			rosterToons <- RosterService.roster_toons.value
		} yield {
			for (user <- users) yield {
				val toons = rosterToons.getOrElse(user.id, Seq.empty)
				UserData(user, toons.find(_.main).map(_.id), toons)
			}
		}
	}

	/*message("request-user") { p =>
		val id = p.as[Int]
		for {
			user <- RosterService.user(id)
			chars <- RosterService.chars(id)
		} send("user-data", (user, chars))
	}*/

	// Allow promoted user to bypass own-character restrictions
	val update_user = if (user.promoted) None else Some(user)

	request("promote-char") { char: Int =>
		RosterService.promoteChar(char, update_user)
	}

	request("disable-char") { char: Int =>
		RosterService.disableChar(char, update_user)
	}

	request("enable-char") { char: Int =>
		RosterService.enableChar(char, update_user)
	}

	request("remove-char") { char: Int =>
		RosterService.removeChar(char, update_user)
	}

	request("update-char") { char: Int =>
		RosterService.refreshChar(char, update_user)
	}

	request("change-role") { (char: Int, role: String) =>
		RosterService.changeRole(char, role, update_user)
	}
}
