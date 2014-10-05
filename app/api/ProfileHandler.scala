package api

import actors.SocketHandler
import play.api.libs.json._
import models._
import models.mysql._
import models.sql._

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

	/**
	 * $:profile:enable
	 * $:profile:disable
	 */
	def handleProfileEnable(arg: JsValue, state: Boolean): MessageResponse = DB.withSession { implicit s =>
		val char = (arg \ "id").as[Int]
		val active = for (c <- Chars if c.id === char && c.owner === socket.user.id && c.main === false) yield c.active

		if (active.update(state) > 0) {
			Chars.notifyUpdate(char)
			MessageSuccess()
		} else {
			MessageFailure("UNABLE_TO_UPDATE")
		}
	}
	
	/**
	 * $:profile:promote
	 */
	def handleProfilePromote(arg: JsValue): MessageResponse = DB.withTransaction { implicit s =>
		val main_id = (arg \ "id").as[Int]
		
		val query = for (c <- Chars if (c.id === main_id || c.main === true) && c.owner === socket.user.id) yield c
		val chars = query.list
		
		if (chars.length < 2) {
			return MessageFailure("UNABLE_TO_PROMOTE")
		}
		
		chars foreach { char =>
			val main = for (c <- Chars if c.id === char.id) yield c.main
			main.update(char.id == main_id)
			Chars.notifyUpdate(char.id)
		}
		
		MessageSuccess()
	}
}
