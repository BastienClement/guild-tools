package api

import actors.SocketHandler
import gt.User
import play.api.libs.json._
import actors.ChatManager._
import akka.pattern.ask
import gt.Global.ExecutionContext

trait ChatHandler {
	this: SocketHandler =>

	/**
	 * $:chat:onlines
	 */
	def handleChatOnlines(): MessageResponse = utils.defer {
		(ChatManagerRef ? ListOnlines).mapTo[Set[Int]]
	}
}
