package channels

import akka.actor.Props
import boopickle.DefaultBasic._
import gtp3._
import models.User
import models.application.ApplicationEvents._
import models.application._
import reactive.ExecutionContext
import utils.SlickAPI._

object ApplyChannel extends ChannelValidator {
	def open(request: ChannelRequest) = {
		if (request.user.roster) request.accept(Props(new ApplyChannel(request.user)))
		else request.reject(1, "Unauthorized")
	}
}

class ApplyChannel(user: User) extends ChannelHandler {
	init {
		ApplicationEvents.subscribe(user)
	}

	akka {
		case UnreadUpdated(apply_id, unread) => send("unread-updated", (apply_id, unread))
		case ApplyUpdated(apply) => send("apply-updated", apply)
		case MessagePosted(message) => send("message-posted", message)
	}

	/**
	  * List of open applys that the user can access
	  */
	request("open-list") {
		Applications.listOpenForUser(user).result
	}

	// Load a specific application data
	/*request("apply-data") { p =>
		val id = p.value.as[Int]
		Applications.getDataVerified(id, user.id, user.member, user.promoted).result.head.run
	}*/

	/**
	  * Request the message feed and body
	  */
	request("apply-feed-body") { id: Int =>
		for {
			body_opt <- Applications.dataChecked(id, user.id, user.member, user.promoted).result.headOption
			body = body_opt.getOrElse(throw new Exception("Access to this application is denied"))
			feed <- ApplicationFeed.forApplicationSorted(id, user.member).result
		} yield (feed, body)
	}

	/**
	  * Update the unread status for an application
	  */
	request("set-seen") { id: Int => ApplicationReadStates.markAsRead(id, user) }

	/**
	  * Post a new message in an application
	  */
	request("post-message") { (apply: Int, body: String, secret: Boolean) =>
		for (_ <- ApplicationFeed.postMessage(user, apply, body, secret)) yield true
	}

	/**
	  * Change an application stage
	  */
	request("change-application-stage") { (apply: Int, stageid: Int) =>
		if (!user.promoted) throw new Exception("Changing application stage requires Promoted status.")
		Applications.changeStage(apply, user, Stage.fromId(stageid))
	}
}
