package api

import actors.RosterManagerActor._
import actors.SocketHandler
import play.api.libs.json._

trait RosterHandler {
	this: SocketHandler =>

	object Roster {
		/**
		 * $:roster:load
		 */
		def handleLoad(): MessageResponse = RosterManager.compositeRoster

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
