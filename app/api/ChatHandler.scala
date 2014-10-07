package api

import actors.SocketHandler
import gt.User
import play.api.libs.json._

trait ChatHandler {
	this: SocketHandler =>

	/**
	 * $:chat:onlines
	 */
	def handleChatOnlines(): MessageResponse = {
		val users = User.onlines.values map (_.toJson)
		MessageResults(JsArray(users.toSeq))
	}
}
