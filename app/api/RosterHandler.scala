package api

import actors.Actors.RosterManager
import actors.SocketHandler
import play.api.libs.json._

trait RosterHandler {
	socket: SocketHandler =>

	object Roster {
		/**
		 * $:roster:load
		 */
		def handleLoad(arg: JsValue): MessageResponse = RosterManager.compositeRoster

		/**
		 * $:roster:user
		 */
		def handleUser(arg: JsValue): MessageResponse = RosterManager.compositeUser((arg \ "id").as[Int])

		/**
		 * $:roster:char
		 */
		def handleChar(arg: JsValue): MessageResponse = RosterManager.char((arg \ "id").as[Int])
	}
}
