package channels

import akka.actor.Props
import gtp3._
import models._
import models.mysql._
import reactive._

object Apply extends ChannelValidator {
	def open(request: ChannelRequest) = {
		if (request.user.roster) request.accept(Props(new Apply(request.user)))
		else request.reject(1, "Unauthorized")
	}
}

class Apply(user: User) extends ChannelHandler {
	init {
		Applys.subscribe(user)
	}

	akka {
		case Applys.UnreadUpdated(apply, unread) => send("unread-updated", (apply, unread))
		case Applys.ApplyUpdated(apply) => send("apply-updated", apply)
	}

	// List of open applys that the user can access
	request("open-list") { p => Applys.openForUser(user.id, user.member).result.run }

	// Check that the current user can access a specific apply
	def canAccess(apply: models.Apply) = apply match {
		// Access to an archived or pending apply require promoted
		case a if a.stage > Applys.TRIAL || a.stage == Applys.PENDING => user.promoted
		// Access to an open (not pending) apply require member or own apply
		case a if a.stage > Applys.PENDING => user.member || a.user == user.id
		// Other use cases are undefined thus not allowed
		case _ => false
	}

	// Load apply data and ensure that the user can access it
	def applyData(id: Int) = for (apply <- Applys.applyById(id).result.headOption.run) yield apply.filter(canAccess)

	request("apply-data") { p => applyData(p.value.as[Int]) }

	request("apply-feed") { p =>
		val id = p.value.as[Int]
		for {
			apply <- applyData(id)
			_ = if (apply.isDefined) () else throw new Exception("Access to this application is denied")
			feed <- ApplyFeed.forApply(id, user.member).result.run
		} yield feed
	}
}
