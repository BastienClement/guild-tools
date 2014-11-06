package api

import actors.RosterManagerActor._
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
			(RosterManager ? Api_ListRoster).mapTo[JsObject]
		}

		/**
		 * $:roster:user
		 */
		def handleUser(arg: JsValue): MessageResponse = {
			(RosterManager ? Api_QueryUser((arg \ "id").as[Int])).mapTo[JsObject]
		}

		/**
		 * $:roster:char
		 */
		def handleChar(arg: JsValue): MessageResponse = {
			(RosterManager ? QueryChar((arg \ "id").as[Int])).mapTo[Option[models.Char]]
		}
	}
}
