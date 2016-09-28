package channels

import actors.StreamService
import actors.StreamService.Events
import akka.actor.Props
import boopickle.DefaultBasic._
import gtp3._
import models.{StreamStatus, User}
import models.live.Streams
import reactive.ExecutionContext
import utils.SlickAPI._

object StreamChannel extends ChannelValidator {
	def open(request: ChannelRequest) = request.accept(Props(new StreamChannel(request.user)))
}

class StreamChannel(val user: User) extends ChannelHandler {
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
	  * Ensure that we do not expose sensitive information.
	  */
	def formatStream(stream: StreamService.ActiveStream): StreamStatus = {
		StreamStatus(stream.meta.user, stream.live, stream.meta.progress, stream.viewersIds)
	}

	/**
	  * Requests the list of currently available streams.
	  */
	request("streams-list") { StreamService.listActiveStreams().map(_ map formatStream) }

	/**
	  * Request a stream ticket.
	  */
	request("request-ticket") { stream: Int =>
		for (ticket <- StreamService.createTicket(stream, user)) yield (ticket.id, ticket.stream)
	}

	/**
	  * Request own stream token and visibility setting.
	  */
	request("own-token-visibility") {
		Streams.filter(_.user === user.id).map(s => (s.token, s.secret, s.progress)).headOption
	}

	/**
	  * Change own stream visibility.
	  */
	request("change-own-visibility") { limited: Boolean =>
		StreamService.changeVisibility(user.id, limited)
	}

	/**
	  * Create a new token for the current user.
	  */
	request("create-token") {
		val token = utils.randomToken()
		val key = utils.randomToken().take(10)
		(for {
			s <- Streams.filter(_.user === user.id).result.headOption
			_ = if (s.nonEmpty) throw new Exception("This account already has an associated streaming key.")
			_ <- Streams.map(s => (s.token, s.user, s.secret, s.progress)) += (token, user.id, key, false)
		} yield ()).transactionally
	}

	/**
	  * Whitelist
	  */
	request("whitelist") { () }
}
