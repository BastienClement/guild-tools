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

		// Send actives list
		for (list <- StreamService.listActiveStreams().map(_ map formatStream)) {
			send("list", list)
		}
	}

	akka {
		case Events.StreamNotify(stream) => send("notify", formatStream(stream))
		case Events.StreamInactive(stream) => send("offline", stream.meta.user)
	}

	/**
	  * Format the stream for the client-side.
	  * Ensure that we do not expose sensitive informations.
	  */
	def formatStream(stream: StreamService.ActiveStream) = {
		(stream.meta.user, stream.live, stream.meta.progress, stream.viewersIds)
	}

	/**
	  * Requests the list of currently available streams.
	  */
	request("streams-list") { p =>
		StreamService.listActiveStreams().map(_ map formatStream)
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
