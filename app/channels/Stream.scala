package channels

import actors.StreamService
import actors.StreamService.Events
import akka.actor.Props
import gtp3._
import models._
import reactive.ExecutionContext

object Stream extends ChannelValidator {
	def open(request: ChannelRequest) = request.accept(Props(new Stream(request.user)))
}

class Stream(val user: User) extends ChannelHandler {
	init {
		Events.subscribe(user)
	}

	akka {
		case Events.StreamNotify(stream) =>
			send("notify", (stream.meta.user, stream.live, stream.viewersIds))

		case Events.StreamInactive(stream) =>
			send("offline", stream.meta.user)
	}

	/**
	  * Requests the list of currently available streams.
	  */
	request("streams-list") { p =>
		for (streams <- StreamService.listActiveStreams()) yield {
			for (stream <- streams) yield {
				(stream.meta.user, stream.live, stream.meta.progress, stream.viewersIds)
			}
		}
	}

	/**
	  * Request a stream token.
	  */
	request("request-token") { p =>
		val stream = p("stream").as[Int]
		(for (ticket <- StreamService.createTicket(stream, user)) yield {
			ticket.id
		}) recover { case _ => throw new Exception("Unable to create the streaming ticket") }
	}

	/**
	  * Whitelist
	  */
	request("whitelist") { p => () }
}
