package api

import actors.Actors.Roster
import actors.SocketHandler
import play.api.libs.json._

trait RosterHandler {
	socket: SocketHandler =>

	object Roster {
		/**
		 * $:roster:load
		 */
		def handleLoad(arg: JsValue): MessageResponse = Roster.compositeRoster

		/**
		 * $:roster:user
		 */
		def handleUser(arg: JsValue): MessageResponse = Roster.compositeUser((arg \ "id").as[Int])

		/**
		 * $:roster:char
		 */
		def handleChar(arg: JsValue): MessageResponse = Roster.char((arg \ "id").as[Int])
	}
}
