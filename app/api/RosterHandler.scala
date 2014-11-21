package api

import actors.Actors.RosterService
import actors.SocketHandler
import play.api.libs.json._

trait RosterHandler {
	socket: SocketHandler =>

	object Roster {
		/**
		 * $:roster:load
		 */
		def handleLoad(arg: JsValue): MessageResponse = RosterService.compositeRoster

		/**
		 * $:roster:user
		 */
		def handleUser(arg: JsValue): MessageResponse = RosterService.compositeUser((arg \ "id").as[Int])

		/**
		 * $:roster:char
		 */
		def handleChar(arg: JsValue): MessageResponse = RosterService.char((arg \ "id").as[Int])
	}
}
