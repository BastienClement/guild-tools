package channels

import actors.Actors._
import gtp3._
import play.api.libs.json.Json

object Chat extends ChannelValidator {
	def open(request: ChannelRequest) = request.accept(new Chat)
}

class Chat extends ChannelHandler with InitHandler with CloseHandler {
	val handlers = Map[String, Handler](
		"away" -> away
	)

	def init() = {
		ChatService.connect(channel)
		channel.send("onlines", ChatService.onlines.map { case (k, v) => Json.arr(k, v) })
	}

	def close() = {
		ChatService.disconnect(channel)
	}

	def away(payload: Payload): Unit = {
		ChatService.setAway(channel, payload.value.as[Boolean])
	}
}
