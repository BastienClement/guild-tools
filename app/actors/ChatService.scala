package actors

import akka.actor.ActorRef
import gtp3.ChannelHandler.SendMessage
import gtp3.{Channel, Payload}
import actors.Actors.Implicits._
import models._
import models.mysql._
import play.api.libs.json._
import gt.Global.ExecutionContext

import scala.collection.mutable
import scala.concurrent.Future
import scala.language.implicitConversions

case class ChatSession(user: User, var away: Boolean, val actors: mutable.Map[ActorRef, Boolean])

trait ChatService extends {
	def connect(actor: ActorRef, user: User): Unit
	def disconnect(actor: ActorRef): Unit

	def getOnlines(): Future[Map[Int, Boolean]]
	def setAway(actor: ActorRef, away: Boolean): Unit

	def roomBacklog(room: Int, user: User, count: Option[Int], limit: Option[Int] = None): Future[Seq[ChatMessage]]
}

class ChatServiceImpl extends ChatService {
	private var sessions = Map[Int, ChatSession]()

	private def broadcast(msg: String, payload: Payload, filter: (User) => Boolean = (_) => true) = {
		for {
			session <- sessions.values if filter(session.user)
			handler <- session.actors.keys
		} handler ! SendMessage(msg, payload)
	}

	def getOnlines(): Future[Map[Int, Boolean]] = sessions map {
		case (user, session) => user -> session.away
	}

	private def updateAway(session: ChatSession) = {
		val away = session.actors.values.forall(away => away)
		if (away != session.away) {
			session.away = away
			broadcast("away-state-changed", Json.arr(session.user.id, away))
		}
	}

	def connect(actor: ActorRef, user: User): Unit = {
		val act = actor -> false

		sessions.get(user.id) match {
			case Some(session) =>
				session.actors += act
				updateAway(session)

			case None =>
				val session = ChatSession(user, false, mutable.Map(act))
				sessions += user.id -> session
				broadcast("connected", user.id)
		}
	}

	def disconnect(actor: ActorRef): Unit = {
		sessions.find {
			case (user, session) => session.actors.contains(actor)
		} foreach {
			case (user, session) =>
				session.actors -= actor
				if (session.actors.size < 1) {
					sessions -= user
					broadcast("disconnected", user)
				}
		}
	}

	def setAway(actor: ActorRef, away: Boolean) = {
		for (session <- sessions.values find (_.actors.contains(actor))) {
			session.actors.update(actor, away)
			updateAway(session)
		}
	}

	def roomBacklog(room: Int, user: User, count: Option[Int], limit: Option[Int] = None): Future[Seq[ChatMessage]] = {
		var query = ChatMessages.filter(_.room === room)
		for (l <- limit)
			query = query.filter(_.id < l)

		val actual_count = count.filter(v => v > 0 && v <= 100).getOrElse(50)
		query.sortBy(_.id.asc).take(actual_count).run
	}
}
