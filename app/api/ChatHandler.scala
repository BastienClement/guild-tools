package api

import actors.Actors.ChatManager
import actors.SocketHandler

trait ChatHandler {
	this: SocketHandler =>

	object Chat {
		/**
		 * $:chat:onlines
		 */
		def handleOnlines(): MessageResponse = ChatManager.onlinesUsers
	}
}
