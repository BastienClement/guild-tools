package actors

import scala.concurrent.Future
import actors.Actors.Implicits._
import akka.actor.{PoisonPill, ActorRef}
import models._
import models.mysql._
import gt.Global.ExecutionContext
import scala.collection.mutable

trait SessionManagerInterface {
	def userById(id: Int): Option[User]
	def userForSession(id: String): Option[User]

	def disconnect(socket: ActorRef): Unit
}

private class Session(val user: User) {
	var sockets = Set[ActorRef]()

	def disconnect(socket: ActorRef): Unit = {
		sockets -= socket
		if (sockets.size < 1) dispose()
	}

	def dispose() = {
		for (socket <- sockets) socket ! PoisonPill
	}
}

class SessionManager extends SessionManagerInterface {
	private var sessions = Set[Session]()

	def userById(id: Int): Option[User] = {
		DB.withSession { s => Users.filter(_.id === id).firstOption(s) }
	}

	def userForSession(id: String): Option[User] = {
		DB.withSession { s => Sessions.filter(_.token === id).map(_.user).firstOption(s) flatMap userById }
	}

	private def sessionForSocket(socket: ActorRef): Option[Session] = {
		sessions.find(_.sockets.contains(socket))
	}

	def disconnect(socket: ActorRef): Unit = {
		for (session <- sessions.find(s => s.sockets.contains(socket))) {
			session.disconnect(socket)
		}
	}
}
