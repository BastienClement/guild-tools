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
		case Applys.MessagePosted(message) => send("message-posted", message)
	}

	// List of open applys that the user can access
	request("open-list") { p => Applys.openForUser(user.id, user.member, user.promoted).result.run }

	// Load a specific application data
	request("apply-data") { p =>
		val id = p.value.as[Int]
		for (apply <- Applys.filter(_.id === id).headOption) yield apply.filter(a => Applys.canAccess(a.user, a.stage, user))
	}

	// Request the message feed and body
	request("apply-feed-body") { p =>
		val id = p.value.as[Int]
		val query = for {
			(owner, stage, body) <- Applys.filter(_.id === id).map(a => (a.user, a.stage, a.data)).result.head
			_ = if (Applys.canAccess(owner, stage, user)) () else throw new Exception("Access to this application is denied")
			feed <- ApplyFeed.forApply(id, user.member).result
		} yield (feed, body)
		query.run
	}

	// Update the unread status for an application
	message("set-seen") { p => Applys.markAsRead(p.value.as[Int], user) }

	// Post a new message in an application
	request("post-message") { p =>
		val apply = p("apply").as[Int]
		val body = p("message").as[String]
		val secret = p("secret").as[Boolean]
		if (secret && !user.member) throw new Exception("Apply cannot posts private messages")
		Applys.postMessage(user, apply, body, secret) map (_ => true)
	}
}