package gt

import Utils.using
import akka.actor.ActorRef
import play.api.Logger
import play.api.Play.current
import play.api.libs.json._
import scala.annotation.tailrec
import scala.collection.mutable
import models._
import models.mysql._
import api.Message

object User {
	var onlines = Map[Int, User]()

	def findByID(id: Int): Option[User] = {
		onlines.get(id) orElse {
			try {
				val user = new User(id)
				Some(user)
			} catch {
				case e: Throwable => None
			}
		}
	}

	def findBySession(session: String): Option[User] = {
		DB.withSession { implicit s =>
			val user = for (s <- Sessions if s.token === session) yield s.user
			user.firstOption flatMap (findByID(_))
		}
	}

	val developers = Set(1647)
	val officier_groups = Set(11)
}

class User(val id: Int) {
	var name = ""
	var group = 0
	var color = ""
	var ready = false

	updatePropreties()

	def updatePropreties(): Unit = DB.withSession { implicit s =>
		val user = Users.filter(_.id === id).first

		name = user.name
		group = user.group
		color = user.color

		// Check if at least one caracter is registered for this user
		val char = for (c <- Chars if c.owner === id) yield c.id
		ready = char.firstOption.isDefined
	}

	val sockets = mutable.Set[Socket]()

	def createSocket(session: String, handler: ActorRef): Socket = {
		using(Socket.create(this, session, handler)) { new_socket =>
			sockets.synchronized { sockets += new_socket }
			DB.withSession { implicit s =>
				val l_a = for (s <- Sessions if s.token === session && s.user === id) yield s.last_access
				l_a.update(NOW())
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
	
	def !(m: Message) = sockets foreach (_ ! m)

	def dispose(): Unit = {
		User.onlines -= id
		User.onlines.values foreach {
			_ ! Message("chat:onlines:update", Json.obj("type" -> "offline", "data" -> id))
		}
		Logger.info("Disposed user: " + toJson())
	}

	def isDev: Boolean = User.developers.exists(_ == id)
	def isOfficer: Boolean = User.officier_groups.exists(_ == group) || isDev

	def toJson(): JsObject = Json.obj(
		"id" -> id,
		"name" -> name,
		"group" -> group,
		"color" -> color,
		"ready" -> ready,
		"officer" -> isOfficer,
		"dev" -> isDev)
		
	User.onlines.values foreach {
		_ ! Message("chat:onlines:update", Json.obj("type" -> "online", "data" -> toJson()))
	}

	User.onlines += (id -> this)
	Logger.info("Created user: " + toJson())
}
