package api

import actors.SocketHandler
import gt.User
import play.api.libs.json._
import actors.RosterManager._
import akka.pattern.ask
import gt.Global.ExecutionContext

trait RosterHandler {
	this: SocketHandler =>

	/**
	 * $:roster:load
	 */
	def handleRosterLoad(): MessageResponse = utils.defer {
		(RosterManagerRef ? Api_ListRoster).mapTo[JsObject]
	}

	/**
	 * $:roster:user
	 */
	def handleRosterUser(arg: JsValue): MessageResponse = utils.defer {
		(RosterManagerRef ? Api_QueryUser((arg \ "id").as[Int])).mapTo[JsObject]
	}

	/**
	 * $:roster:char
	 */
	def handleRosterChar(arg: JsValue): MessageResponse = utils.defer {
		(RosterManagerRef ? QueryChar((arg \ "id").as[Int])).mapTo[Option[models.Char]]
	}
}
