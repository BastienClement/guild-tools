package channels

import akka.actor.{ActorRef, Props}
import gtp3._
import models._

object Roster extends ChannelValidator {
	def open(request: ChannelRequest) = request.accept(Props(new Roster(request.user)))
}

class Roster(val user: User) extends ChannelHandler {
	init {

	}

	stop {

	}
}
