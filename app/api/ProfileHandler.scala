package api

import actors.SocketHandler
import play.api.libs.json._
import models._
import models.mysql._
import models.sql._
import gt.Bnet
import java.util.Date

trait ProfileHandler { this: SocketHandler =>
	/**
	 * Validate role for DB queries
	 */
	private def checkRole(role: String): String = {
		role match {
			case "TANK" | "HEALING" => role
			case _ => "DPS"
		}
	}

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
		val active = for (c <- Chars if c.id === char && c.owner === user.id && c.main === false) yield c.active

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

		val query = for (c <- Chars if (c.id === main_id || c.main === true) && c.owner === user.id) yield c
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

	/**
	 * $:profile:remove
	 */
	def handleProfileRemove(arg: JsValue): MessageResponse = DB.withSession { implicit s =>
		val id = (arg \ "id").as[Int]
		val char = for (c <- Chars if c.id === id && c.owner === user.id && c.active === false) yield c

		if (char.delete > 0) {
			Chars.notifyDelete(id)
			MessageSuccess()
		} else {
			MessageFailure("CHAR_NOT_FOUND")
		}
	}

	/**
	 * $:profile:role
	 */
	def handleProfileRole(arg: JsValue): MessageResponse = DB.withSession { implicit s =>
		val id = (arg \ "id").as[Int]
		val role = checkRole((arg \ "role").as[String])

		val row = for (c <- Chars if c.id === id && c.owner === user.id && c.role =!= role) yield c.role
		if (row.update(role) > 0) {
			Chars.notifyUpdate(id)
			MessageSuccess()
		} else {
			MessageFailure("CHAR_NOT_FOUND")
		}
	}

	/**
	 * $:profile:check
	 */
	def handleProfileCheck(arg: JsValue): MessageResponse = DB.withSession { implicit s =>
		val server = (arg \ "server").as[String]
		val name = (arg \ "name").as[String]

		val char = for (c <- Chars if c.server === server && c.name === name) yield c.id
		MessageResults(char.firstOption.isEmpty)
	}

	/**
	 * $:profile:register
	 */
	def handleProfileRegister(arg: JsValue): MessageResponse = {
		val server = (arg \ "server").as[String]
		val name = (arg \ "char").as[String]
		val role = checkRole((arg \ "role").as[String])

		if (!server.matches("""^[a-z\-]+$""") || name.matches("""\/|\.|\?""")) {
			return MessageFailure("INVALID_DATA")
		}

		Bnet.query(s"/character/$server/$name", ("fields" -> "items")) map { char =>
			DB.withTransaction { implicit s =>
				val main = for (c <- Chars if c.main === true && c.owner === user.id) yield c.id

				val template = Char(
					id = 0,
					name = name,
					server = server,
					owner = socket.user.id,
					main = main.firstOption.isEmpty,
					active = true,
					`class` = (char \ "class").as[Int],
					race = (char \ "race").as[Int],
					gender = (char \ "gender").as[Int],
					level = (char \ "level").as[Int],
					achievements = (char \ "achievementPoints").as[Int],
					thumbnail = (char \ "thumbnail").as[String],
					ilvl = (char \ "items" \ "averageItemLevel").as[Int],
					role = role,
					last_update = (new Date()).getTime())

				val id: Int = (Chars returning Chars.map(_.id)) += template
				Chars.notifyCreate(template.copy(id = id))

				if (template.main) {
					user.updatePropreties()
				}

				MessageSuccess()
			}
		} getOrElse {
			MessageFailure("CHAR_NOT_FOUND")
		}
	}
}
