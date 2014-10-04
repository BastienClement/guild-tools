package api

import actors.SocketHandler
import play.api.libs.json._
import models._
import models.mysql._

trait ProfileHandler { this: SocketHandler =>
	/**
	 * $:profile:load
	 */
	def handleProfileLoad(arg: JsValue): MessageResponse = DB.withSession { implicit s =>
		val user_id = (arg \ "id").as[Int]

		val user = Users.filter(_.id === user_id).first
		val chars = Chars.filter(_.owner === user_id).list

		MessageResults(Json.obj("user" -> user, "chars" -> chars.toSeq))
	}
}
