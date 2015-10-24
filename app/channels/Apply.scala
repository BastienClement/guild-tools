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
		case Applys.UnreadUpdated(apply_id, unread) => send("unread-updated", (apply_id, unread))
		case Applys.ApplyUpdated(apply) => send("apply-updated", apply)
	}

	// List of open applys that the user can access
	request("open-list") { p => Applys.openForUser(user.id, user.member).result.run }

	// Check that the current user can access a specific apply
	val canAccess: (Int, Int) => Boolean = {
		// Access to an archived or pending apply require promoted
		case (owner, stage) if stage > Applys.TRIAL || stage == Applys.PENDING => user.promoted
		// Access to an open (not pending) apply require member or own apply
		case (owner, stage) if stage > Applys.PENDING => user.member || owner == user.id
		// Other use cases are undefined thus not allowed
		case _ => false
	}

	// Load a specific application data
	request("apply-data") { p =>
		val id = p.value.as[Int]
		for (apply <- Applys.filter(_.id === id).headOption) yield apply.filter(a => canAccess(a.user, a.stage))
	}

	// Request the message feed and body
	request("apply-feed-body") { p =>
		val id = p.value.as[Int]
		val query = for {
			(owner, stage, body) <- Applys.filter(_.id === id).map(a => (a.user, a.stage, a.data)).result.head
			_ = if (canAccess(owner, stage)) () else throw new Exception("Access to this application is denied")
			feed <- ApplyFeed.forApply(id, user.member).result
		} yield (feed, body)
		query.run
	}

	// Update the unread status for an application
	message("set-seen") { p => Applys.markAsRead(p.value.as[Int], user) }
}
