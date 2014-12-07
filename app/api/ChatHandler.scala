package api

import actors.Actors.ChatService
import actors.SocketHandler
import play.api.libs.json.{Json, JsValue}

trait ChatHandler {
	socket: SocketHandler =>

	object Chat {
		/**
		 * $:chat:sync
		 */
		def handleSync(arg: JsValue): MessageResponse = {
			Json.obj(
				"onlines" -> ChatService.onlines,
				"shoutbox" -> ChatService.loadShoutbox())
		}

		/**
		 * $:chat:shoutbox:send
		 */
		def handleShoutboxSend(arg: JsValue): MessageResponse = {
			val msg = (arg \ "msg").as[String].replaceAll("^\\s+|\\s+$", "")
			if (msg.length() > 0) ChatService.sendShoutbox(user, msg)
			MessageSuccess
		}
	}
}
