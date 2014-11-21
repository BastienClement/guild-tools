package api

import actors.Actors.BattleNet
import actors.Actors.RosterService
import actors.SocketHandler
import gt.Global.ExecutionContext
import models._
import models.mysql._
import play.api.libs.json._

trait ProfileHandler {
	socket: SocketHandler =>

	object Profile {
		/**
		 * Validate role for DB queries
		 */
		def validateRole(role: String): String = if (Chars.validateRole(role)) role else "DPS"

		/**
		 * $:profile:load
		 */
		def handleLoad(arg: JsValue): MessageResponse = DB.withSession { implicit s =>
			val user_id = (arg \ "id").as[Int]

			//val user = Users.filter(_.id === user_id).first
			//val chars = Chars.filter(_.owner === user_id).list

			//socket.unbindEvents()

			//MessageResults(Json.obj("user" -> user, "chars" -> chars.toSeq))
			MessageSuccess
		}

		/**
		 * $:profile:enable
		 * $:profile:disable
		 */
		def handleEnable(state: Boolean)(arg: JsValue): MessageResponse = DB.withSession { implicit s =>
			val char = (arg \ "id").as[Int]
			val query = Chars.filter(c => c.id === char && c.owner === user.id && c.main === false)

			if (query.map(_.active).update(state) > 0) {
				Chars.notifyUpdate(query.first)
				MessageSuccess
			} else {
				MessageFailure("UNABLE_TO_UPDATE")
			}
		}

		/**
		 * $:profile:promote
		 */
		def handlePromote(arg: JsValue): MessageResponse = DB.withTransaction { implicit s =>
			val main_id = (arg \ "id").as[Int]

			val query = for (c <- Chars if (c.id === main_id || c.main === true) && c.owner === user.id) yield c
			val chars = query.list

			if (chars.length < 2) {
				return MessageFailure("UNABLE_TO_PROMOTE")
			}

			chars foreach { char =>
				val query = Chars.filter(_.id === char.id)
				if (query.map(_.main).update(char.id == main_id) > 0) {
					Chars.notifyUpdate(query.first)
				}
			}

			MessageSuccess
		}

		/**
		 * $:profile:remove
		 */
		def handleRemove(arg: JsValue): MessageResponse = DB.withSession { implicit s =>
			val id = (arg \ "id").as[Int]
			val char = for (c <- Chars if c.id === id && c.owner === user.id && c.active === false) yield c

			if (char.delete > 0) {
				Chars.notifyDelete(id)
				MessageSuccess
			} else {
				MessageFailure("CHAR_NOT_FOUND")
			}
		}

		/**
		 * $:profile:role
		 */
		def handleRole(arg: JsValue): MessageResponse = DB.withSession { implicit s =>
			val id = (arg \ "id").as[Int]
			val role = validateRole((arg \ "role").as[String])

			val query = Chars.filter(c => c.id === id && c.owner === user.id)
			if (query.filter(_.role =!= role).map(_.role).update(role) > 0) {
				Chars.notifyUpdate(query.first)
				MessageSuccess
			} else {
				MessageFailure("CHAR_NOT_FOUND")
			}
		}

		/**
		 * $:profile:check
		 */
		def handleCheck(arg: JsValue): MessageResponse = DB.withSession { implicit s =>
			val server = (arg \ "server").as[String]
			val name = (arg \ "name").as[String]

			val char = for (c <- Chars if c.server === server && c.name === name) yield c.id
			MessageResults(char.firstOption.isEmpty)
		}

		/**
		 * $:profile:register
		 */
		def handleRegister(arg: JsValue): MessageResponse = {
			val server = (arg \ "server").as[String]
			val name = (arg \ "char").as[String]
			val role = validateRole((arg \ "role").as[String])

			if (!server.matches( """^[a-z\-]+$""") || name.matches( """\/|\.|\?""")) {
				return MessageFailure("INVALID_DATA")
			}

			BattleNet.fetchChar(server, name) map { char =>
				DB.withSession { implicit s =>
					val main = for (c <- Chars if c.main === true && c.owner === user.id) yield c.id
					val template = char.copy(owner = user.id, main = main.firstOption.isEmpty, role = role)

					val id: Int = (Chars returning Chars.map(_.id)) += template
					Chars.notifyCreate(template.copy(id = id))

					MessageSuccess
				}
			}
		}

		/**
		 * $:profile:refresh
		 */
		def handleRefresh(arg: JsValue): MessageResponse = {
			val id = (arg \ "id").as[Int]
			RosterService.refreshChar(id)
			MessageSuccess
		}
	}
}
