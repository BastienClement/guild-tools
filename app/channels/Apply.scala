package channels

import akka.actor.Props
import gtp3._
import models._
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

	request("open-list") { p => Applys.openForUser(user).run }

	request("apply-data") { p =>
		for {
			d <- Applys.applyById(p.value.as[Int]).headOption.run
		} yield {
			d filter {
				// Access to an archived or pending apply require promoted
				case a if a.stage > Applys.TRIAL || a.stage == Applys.PENDING => user.promoted

				// Access to an open (not pending) apply require member or own apply
				case a if a.stage > Applys.PENDING => user.member || a.user == user.id

				// Other use cases are undefined thus not allowed
				case _ => false
			}
		}
	}
}
