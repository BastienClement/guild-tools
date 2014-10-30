package api

import actors.RosterManager._
import actors.SocketHandler
import akka.pattern.ask
import play.api.libs.json._

trait RosterHandler {
	this: SocketHandler =>

	object Roster {
		/**
		 * $:roster:load
		 */
		def handleLoad(): MessageResponse = {
			(RosterManagerRef ? Api_ListRoster).mapTo[JsObject]
		}

		/**
		 * $:roster:user
		 */
		def handleUser(arg: JsValue): MessageResponse = {
			(RosterManagerRef ? Api_QueryUser((arg \ "id").as[Int])).mapTo[JsObject]
		}

		/**
		 * $:roster:char
		 */
		def handleChar(arg: JsValue): MessageResponse = {
			(RosterManagerRef ? QueryChar((arg \ "id").as[Int])).mapTo[Option[models.Char]]
		}
	}
}
