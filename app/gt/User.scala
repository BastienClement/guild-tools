package gt

import scala.annotation.tailrec
import scala.collection.mutable

import play.api.Play.current
import play.api.db.DB
import play.api.libs.json.Json
import play.api.libs.json.Json.toJsFieldJsValueWrapper

import Utils.use
import akka.actor.ActorRef
import anorm.ParameterValue.toParameterValue
import anorm.SqlStringInterpolation

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
			case _: Throwable => None
		}
	}
}

class User(val id: Int) {
	var name = ""
	var group = 0
	var color = ""
	private var pass = ""

	updatePropreties()

	def updatePropreties() = {
		DB.withConnection { implicit c =>
			val row = SQL"SELECT username, group_id, user_colour, user_password FROM phpbb_users WHERE user_id = $id LIMIT 1".single()
			name = row[String]("username")
			group = row[Int]("group_id")
			color = row[String]("user_colour")
			pass = row[String]("user_password")
		}
	}

	private val sockets = mutable.Set[Socket]()

	def createSocket(handler: ActorRef): Socket = {
		use(Socket.create(this, handler)) { sock =>
			sockets.synchronized { sockets += sock }
		}
	}

	def removeSocket(socket: Socket): Unit = {
		sockets.synchronized { sockets -= socket }
		if (sockets.size < 1) {
			dispose()
		}
	}

	def dispose(): Unit = {
		User.users -= id
		println("Disposed user " + toJson())
	}

	println("Created user " + toJson())

	def toJson() = Json.obj("id" -> id, "name" -> name, "group" -> group, "color" -> color)
}
