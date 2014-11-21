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
	}
}
