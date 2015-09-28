package actors

import actors.Actors.ActorImplicits
import actors.ChatService._
import akka.actor.ActorRef
import models._
import models.mysql._
import utils.PubSub

import scala.collection.mutable
import scala.concurrent.Future
import scala.language.implicitConversions

object ChatService {
	case class UserConnect(user: User)
	case class UserAway(user: User, away: Boolean)
	case class UserDisconnect(user: User)

	private case class ChatSession(user: User, var away: Boolean, val actors: mutable.Map[ActorRef, Boolean])
}

trait ChatService extends PubSub[User] with ActorImplicits {
	private var sessions = Map[Int, ChatSession]()

	def onlines(): Future[Map[Int, Boolean]] = sessions map {
		case (user, session) => user -> session.away
	}

	private def updateAway(session: ChatSession) = {
		val away = session.actors.values.forall(away => away)
		if (away != session.away) {
			session.away = away
			this !# UserAway(session.user, away)
		}
	}

	override def subscribe(actor: ActorRef, user: User) = {
		super.subscribe(actor, user)
		val act = actor -> false

		sessions.get(user.id) match {
			case Some(session) =>
				session.actors += act
				updateAway(session)

			case None =>
				val session = ChatSession(user, false, mutable.Map(act))
				sessions += user.id -> session
				this !# UserConnect(session.user)
		}
	}

	override def unsubscribe(actor: ActorRef) = {
		super.unsubscribe(actor)
		sessions.find {
			case (user, session) => session.actors.contains(actor)
		} foreach {
			case (user, session) =>
				session.actors -= actor
				if (session.actors.size < 1) {
					sessions -= user
					this !# UserDisconnect(session.user)
				} else {
					updateAway(session)
				}
		}
	}

	def setAway(actor: ActorRef, away: Boolean) = {
		for (session <- sessions.values find (_.actors.contains(actor))) {
			session.actors.update(actor, away)
			updateAway(session)
		}
	}

	def roomBacklog(room: Int, user: Option[User] = None, count: Option[Int] = None, limit: Option[Int] = None): Future[Seq[ChatMessage]] = {
		var query = ChatMessages.filter(_.room === room)
		for (l <- limit)
			query = query.filter(_.id < l)

		val actual_count = count.filter(v => v > 0 && v <= 100).getOrElse(50)
		query.sortBy(_.id.asc).take(actual_count).run
	}
}

class ChatServiceImpl extends ChatService
