package api

import actors.ChatManagerActor._
import actors.SocketHandler
import akka.pattern.ask

trait ChatHandler {
	this: SocketHandler =>

	object Chat {
		/**
		 * $:chat:onlines
		 */
		def handleOnlines(): MessageResponse = {
			(ChatManager ? ListOnlines).mapTo[Set[Int]]
		}
	}
}
