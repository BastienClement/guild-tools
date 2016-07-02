package channels

import akka.actor.Props
import gtp3._
import models._
import models.mysql._
import models.application._
import reactive.ExecutionContext
import ApplicationEvents._

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
	request("open-list") { p => Applications.listOpenForUser(user).result }

	// Load a specific application data
	/*request("apply-data") { p =>
		val id = p.value.as[Int]
		Applications.getDataVerified(id, user.id, user.member, user.promoted).result.head.run
	}*/

	/**
	  * Request the message feed and body
	  */
	request("apply-feed-body") { p =>
		val id = p.value.as[Int]
		for {
			body_opt <- Applications.dataChecked(id, user.id, user.member, user.promoted).result.headOption
			body = body_opt.getOrElse(throw new Exception("Access to this application is denied"))
			feed <- ApplicationFeed.forApplicationSorted(id, user.member).result
		} yield (feed, body)
	}

	/**
	  * Update the unread status for an application
	  */
	request("set-seen") { p => ApplicationReadStates.markAsRead(p.value.as[Int], user) }

	/**
	  * Post a new message in an application
	  */
	request("post-message") { p =>
		val apply = p("apply").as[Int]
		val body = p("message").as[String]
		val secret = p("secret").as[Boolean]
		for (_ <- ApplicationFeed.postMessage(user, apply, body, secret)) yield true
	}

	/**
	  * Change an application stage
	  */
	request("change-application-stage") { p =>
		if (!user.promoted) {
			throw new Exception("Changing application stage requires Promoted status.")
		}

		val apply = p("apply").as[Int]
		val stage = Stage.fromId(p("stage").as[Int])

		Applications.changeStage(apply, user, stage)
	}
}
