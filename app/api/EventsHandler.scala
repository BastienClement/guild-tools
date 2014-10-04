package api

import actors.SocketHandler
import play.api.libs.json._

trait EventsHandler { this: SocketHandler =>
	/**
	 * $:events:bind
	 */
	def handleEventsBind(arg: JsValue): MessageResponse = {
		val events = (arg \ "events").as[Set[String]]
		socket.boundEvents = events
		MessageSuccess()
	}

	/**
	 * $:events:unbind
	 */
	def handleEventsUnbind(): MessageResponse = {
		socket.boundEvents = Set()
		MessageSuccess()
	}
}