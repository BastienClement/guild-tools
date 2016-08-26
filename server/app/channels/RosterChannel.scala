package channels

import actors.RosterService
import actors.RosterService.{ToonDeleted, ToonUpdated}
import akka.actor.Props
import api.Roster.UserData
import boopickle.DefaultBasic._
import gtp3._
import model.User
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

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

	def buildUserData(user: User): Future[UserData] = {
		for (toons <- RosterService.toons(user.id)) yield {
			UserData(user, toons.find(_.main).map(_.id), toons)
		}
	}

	request("load-roster") {
		RosterService.roster_users.flatMap { users =>
			Future.sequence(users.map(buildUserData))
		}
	}

	request("load-user") { id: Int =>
		RosterService.user(id).flatMap(buildUserData)
	}

	request("load-user-toon") { id: Int =>
		RosterService.toonOwner(id).flatMap(RosterService.user).flatMap(buildUserData)
	}

	// Allow promoted user to bypass own-character restrictions
	val update_user = if (user.promoted) None else Some(user)

	request("promote-toon") { toon: Int =>
		RosterService.promoteToon(toon, update_user)
	}

	request("disable-toon") { toon: Int =>
		RosterService.disableToon(toon, update_user)
	}

	request("enable-toon") { toon: Int =>
		RosterService.enableToon(toon, update_user)
	}

	request("remove-toon") { toon: Int =>
		RosterService.removeToon(toon, update_user)
	}

	request("update-toon") { toon: Int =>
		RosterService.refreshToon(toon, update_user)
	}

	request("change-toon-spec") { (toon: Int, spec: Int) =>
		RosterService.changeSpec(toon, spec, update_user)
	}
}
