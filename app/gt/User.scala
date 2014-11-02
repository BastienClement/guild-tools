package gt

import actors.ChatManager._
import akka.actor.ActorRef
import api._
import models._
import models.mysql._
import play.api.Logger
import play.api.libs.json._
import utils.SmartTimestamp

object User {
	var onlines = Map[Int, User]()

	def disposed(user: User): Unit = this.synchronized {
		onlines -= user.id
		ChatManagerRef ! UserLogout(user)
	}

	def findByID(id: Int): Option[User] = this.synchronized {
		val user = onlines.get(id) orElse {
			Some(new User(id))
		} filter { user =>
			try {
				user.updatePropreties()
			} catch {
				case e: Throwable => false
			}
		}

		user foreach { u =>
			if (!onlines.contains(u.id)) {
				ChatManagerRef ! UserLogin(u)
				User.onlines += (id -> u)
			}
		}

		user
	}

	def findBySession(session: String): Option[User] = {
		DB.withSession { implicit s =>
			val user = for (s <- Sessions if s.token === session) yield s.user
			user.firstOption flatMap { u => findByID(u) }
		}
	}

	val developers = Set(1647)
	val officier_groups = Set(11)
}

class User private(val id: Int) {
	var name = ""
	var group = 0
	var color = ""
	var ready = false

	var developer = false
	var officer = false

	var sockets = Set[Socket]()

	/**
	 * Fetch user propreties
	 */
	def updatePropreties(): Boolean = DB.withSession { implicit s =>
		// Basic attributes
		val user = Users.filter(_.id === id).first
		name = user.name
		group = user.group
		color = user.color

		// Check group authorization
		if (!AuthHelper.allowedGroups.contains(group)) {
			sockets foreach { _.close("Unallowed") }
			return false
		}

		// Check if at least one caracter is registered for this user
		val char = for (c <- Chars if c.owner === id) yield c.id
		ready = char.firstOption.isDefined

		// Access rights
		developer = User.developers.contains(id)
		officer = developer || User.officier_groups.contains(group)

		true
	}

	/**
	 * Create a new socket object for this user
	 */
	def createSocket(session: String, handler: ActorRef): Socket = {
		// Create and register a new socket
		val socket = Socket.create(this, session, handler)
		this.synchronized { sockets += socket }

		// Update gt_sessions.last_access
		DB.withSession { implicit s =>
			val l_a = for (s <- Sessions if s.token === session && s.user === id) yield s.last_access
			l_a.update(SmartTimestamp.now)
		}

		socket
	}

	/**
	 * Remove a dead socket from this user
	 */
	def removeSocket(socket: Socket): Unit = {
		this.synchronized {
			sockets -= socket
			if (sockets.size < 1) {
				dispose()
			}
		}
	}

	/**
	 * Send a message or an event to every socket for this user
	 */
	def !(m: Message): Unit = sockets foreach (_ ! m)
	def !#(e: Event): Unit = sockets foreach (_ ! e)

	/**
	 * No more socket available, user is now disconnected
	 */
	private def dispose(): Unit = {
		// Remove this user from online list
		User.disposed(this)
	}

	/**
	 * Generate the JSON representation for this user
	 */
	def asJson: JsObject = Json.obj(
		"id" -> id,
		"name" -> name,
		"group" -> group,
		"color" -> color,
		"ready" -> ready,
		"officer" -> officer,
		"dev" -> developer)
}
