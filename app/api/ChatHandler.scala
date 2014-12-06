package api

import actors.Actors.ChatService
import actors.SocketHandler
import play.api.libs.json.JsValue

trait ChatHandler {
	socket: SocketHandler =>

	object Chat {
		/**
		 * $:chat:onlines
		 */
		def handleOnlines(arg: JsValue): MessageResponse = ChatService.onlines

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
