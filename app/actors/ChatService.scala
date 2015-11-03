package actors

import actors.ChatService._
import channels.Chat
import models._
import models.mysql._
import reactive.ExecutionContext
import scala.collection.mutable
import scala.concurrent.Future
import scala.language.implicitConversions
import utils.PubSub

private[actors] class ChatServiceImpl extends ChatService

object ChatService extends StaticActor[ChatService, ChatServiceImpl]("ChatService") {
	/**
	  * A new user is now connected to the chat system.
	  * This user cannot be away.
	  */
	case class UserConnected(user: User)

	/**
	  * The away state for a user changed.
	  */
	case class UserAway(user: User, away: Boolean)

	/**
	  * A previously connected user is now disconnected.
	  */
	case class UserDisconnected(user: User)

	/**
	  * A new ChatSession is created for every connected user and stores user's sockets
	  * and the corresponding away state for each of them.
	  */
	private case class ChatSession(user: User, var away: Boolean, actors: mutable.Map[ActorTag[Chat], Boolean])
}

/**
  * Provides instant-messaging services.
  */
trait ChatService extends PubSub[User] {
	private var sessions = Map[Int, ChatSession]()

	/**
	  * Returns the list of online users and their away states.
	  */
	def onlines(): Future[Map[Int, Boolean]] = Future {
		sessions map {
			case (user, session) => user -> session.away
		}
	}

	/**
	  * Update away state for a specific session.
	  * This method is called each time the away state of one of the user's sockets
	  * is changed and compute the overall away state of the session.
	  */
	private def updateAway(session: ChatSession) = {
		val away = session.actors.values.forall(away => away)
		if (away != session.away) {
			session.away = away
			this !# UserAway(session.user, away)
		}
	}

	/**
	  * Subscribes an actor to the event feed of this service.
	  * Also add the socket to the corresponding chat session.
	  */
	override def subscribe(actor: ActorTag[Chat], user: User) = {
		super.subscribe(actor, user)
		val act = actor -> false

		sessions.get(user.id) match {
			case Some(session) =>
				session.actors += act
				updateAway(session)

			case None =>
				val session = ChatSession(user, false, mutable.Map(act))
				sessions += user.id -> session
				this !# UserConnected(session.user)
		}
	}

	/**
	  * Unsubscribes an actor from the event feed.
	  * Also remove the socket from the corresponding chat session.
	  */
	override def unsubscribe(actor: ActorTag[Chat]) = {
		super.unsubscribe(actor)
		sessions.find {
			case (user, session) => session.actors.contains(actor)
		} foreach {
			case (user, session) =>
				session.actors -= actor
				if (session.actors.size < 1) {
					sessions -= user
					this !# UserDisconnected(session.user)
				} else {
					updateAway(session)
				}
		}
	}

	/**
	  * Changes the away state of a specific socket.
	  * @todo Make the ActorTag implicit
	  */
	def setAway(actor: ActorTag[Chat], away: Boolean) = {
		for (session <- sessions.values find (_.actors.contains(actor))) {
			session.actors.update(actor, away)
			updateAway(session)
		}
	}

	/**
	  * Returns a room messages backlog.
	  * @todo Replace by a MessageQuery system.
	  */
	def roomBacklog(room: Int, user: Option[User] = None, count: Option[Int] = None, limit: Option[Int] = None): Future[Seq[ChatMessage]] = {
		var query = ChatMessages.filter(_.room === room)
		for (l <- limit)
			query = query.filter(_.id < l)

		val actual_count = count.filter(v => v > 0 && v <= 100).getOrElse(50)
		query.sortBy(_.id.asc).take(actual_count).run
	}
}
