package channels

import actors.Actors._
import gtp3._
import play.api.libs.json.Json

object Chat extends ChannelValidator {
	def open(request: ChannelRequest) = request.accept(new Chat)
}

class Chat extends ChannelHandler with InitHandler with CloseHandler {
	val handlers = Map[String, Handler](
		"set-away" -> setAway
	)

	def init() = {
		ChatService.connect(channel)

		val onlines = for ((user, away) <- ChatService.onlines) yield Json.arr(user, away)
		channel.send("onlines", onlines)
	}

	def close() = {
		ChatService.disconnect(channel)
	}

	def setAway(payload: Payload): Unit = {
		ChatService.setAway(channel, payload.value.as[Boolean])
	}
}
