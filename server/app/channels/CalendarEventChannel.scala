package channels

import akka.actor.Props
import gtp3.{ChannelHandler, ChannelRequest, ChannelValidator}
import models.User

object CalendarEventChannel extends ChannelValidator {
	def open(request: ChannelRequest) = {
		if (request.user.fs) request.accept(Props(new CalendarEventChannel(request.user)))
		else request.reject(1, "Unauthorized")
	}
}

class CalendarEventChannel(user: User) extends ChannelHandler {

}
