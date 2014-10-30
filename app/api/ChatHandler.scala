package api

import actors.ChatManager._
import actors.SocketHandler
import akka.pattern.ask

trait ChatHandler {
	this: SocketHandler =>

	object Chat {
		/**
		 * $:chat:onlines
		 */
		def handleOnlines(): MessageResponse = {
			(ChatManagerRef ? ListOnlines).mapTo[Set[Int]]
		}
	}
}
