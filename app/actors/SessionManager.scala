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

private case class Session(user: User, var sockets: Set[ActorRef])

class SessionManager extends SessionManagerInterface {
	private var sessions = Set[Session]()

	def userById(id: Int): Option[User] = {
		DB.withSession { s => Users.filter(_.id === id).firstOption(s) }
	}

	def userForSession(id: String): Option[User] = {
		DB.withSession { s => Sessions.filter(_.token === id).map(_.user).firstOption(s) flatMap userById }
	}

	/**
	* Return the session related to a specific socket
	*/
	private def sessionForSocket(socket: ActorRef): Option[Session] = {
		sessions.find(_.sockets.contains(socket))
	}

	/**
	* Handle session end & cleanup
	*/
	private def disposeSession(session: Session): Unit = {
		for (socket <- session.sockets) socket ! PoisonPill
		sessions -= session
	}

	/**
	* Handle socket disconnected
	*/
	def disconnect(socket: ActorRef): Unit = {
		for (session <- sessionForSocket(socket)) {
			session.sockets -= socket
			if (session.sockets.size < 1) {
				disposeSession(session)
			}
		}
	}
}
