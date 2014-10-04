package gt

import Utils.using
import akka.actor.ActorRef
import play.api.Logger
import play.api.Play.current
import play.api.libs.json._
import scala.annotation.tailrec
import scala.collection.mutable
import models._
import models.MySQL._
import java.util.Date
import java.sql.Timestamp

object User {
	val users = MapBuilder((id: Int) => { createByID(id) })

	def findByID(id: Int): Option[User] = users.get(id)

	def findBySession(session: String): Option[User] = {
		DB.withSession { implicit s =>
			Sessions.filter(_.token === session).firstOption flatMap { session =>
				findByID(session.user)
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
		DB.withSession { implicit s =>
			val user = Users.filter(_.id === id).first

			name = user.name
			group = user.group
			color = user.color

			// Check if at least one caracter is registered for this user
			val char = Chars.filter(_.owner === id).firstOption
			ready = char.isDefined
		}
	}

	val sockets = mutable.Set[Socket]()

	def createSocket(session: String, handler: ActorRef): Socket = {
		using(Socket.create(this, session, handler)) { new_socket =>
			sockets.synchronized { sockets += new_socket }
			DB.withSession { implicit s =>
				val q = Sessions.filter(s => s.token === session && s.user === id).map(_.last_access)
				q.update(new Timestamp(new Date().getTime()))
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

	def chars = {
		DB.withSession { implicit c =>
			
		}
	}

	def dispose(): Unit = {
		User.users -= id
		Logger.info("Disposed user: " + toJson())
	}

	def toJson(): JsObject = Json.obj("id" -> id, "name" -> name, "group" -> group, "color" -> color, "ready" -> ready)

	Logger.info("Created user: " + toJson())
}
