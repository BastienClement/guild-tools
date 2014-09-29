package gt

import Utils.using
import akka.actor.ActorRef
import anorm.ParameterValue.toParameterValue
import anorm.SqlStringInterpolation
import play.api.Logger
import play.api.Play.current
import play.api.db.DB
import play.api.libs.json.{ JsObject, Json }
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import scala.annotation.tailrec
import scala.collection.mutable

object User {
	val users = MapBuilder((id: Int) => { createByID(id) })

	def findByID(id: Int): Option[User] = users.get(id)

	def findBySession(session: String): Option[User] = {
		DB.withConnection { implicit c =>
			val row = SQL"SELECT user FROM gt_sessions WHERE token = $session LIMIT 1".singleOpt()
			row.flatMap { row =>
				findByID(row[Int]("user"))
			}
		}
	}

	def createByID(id: Int): Option[User] = {
		try {
			Some(new User(id))
		} catch {
			case e: Throwable => None
		}
	}
}

class User(val id: Int) {
	var name = ""
	var group = 0
	var color = ""
	var ready = false

	updatePropreties()

	def updatePropreties(): Unit = {
		DB.withConnection { implicit c =>
			val user_row = SQL"SELECT username, group_id, user_colour FROM phpbb_users WHERE user_id = $id LIMIT 1".single()
			name = user_row[String]("username")
			group = user_row[Int]("group_id")
			color = user_row[String]("user_colour")

			// Check if at least one caracter is registered for this user
			ready = SQL"SELECT id FROM gt_chars WHERE owner = $id LIMIT 1".singleOpt().isDefined
		}
	}

	val sockets = mutable.Set[Socket]()

	def createSocket(session: String, handler: ActorRef): Socket = {
		using(Socket.create(this, session, handler)) { new_socket =>
			sockets.synchronized { sockets += new_socket }
			DB.withConnection { implicit c =>
				SQL"UPDATE gt_sessions SET last_access = NOW() WHERE token = $session AND user = $id LIMIT 1".executeUpdate()
			}
		}
	}

	def removeSocket(socket: Socket): Unit = {
		sockets.synchronized {
			sockets -= socket
			if (sockets.size < 1) {
				dispose()
			}
		}
	}

	def dispose(): Unit = {
		User.users -= id
		Logger.info("Disposed user: " + toJson())
	}

	def toJson(): JsObject = Json.obj("id" -> id, "name" -> name, "group" -> group, "color" -> color, "ready" -> ready)

	Logger.info("Created user: " + toJson())
}
