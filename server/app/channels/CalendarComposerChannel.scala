package channels

import akka.actor.Props
import gtp3.{ChannelHandler, ChannelRequest, ChannelValidator}

object CalendarComposerChannel extends ChannelValidator {
	def open(request: ChannelRequest) = {
		if (request.user.promoted) request.accept(Props(new CalendarComposerChannel))
		else request.reject(1, "Unauthorized")
	}
}

class CalendarComposerChannel extends ChannelHandler {

}

