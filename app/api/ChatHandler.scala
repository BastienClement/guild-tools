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
		val users = User.onlines.values map (_.asJson)
		MessageResults(JsArray(users.toSeq))
	}
}
