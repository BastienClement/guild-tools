package actors

import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util.{Success, Try}
import actors.Actors.Dispatcher
import akka.actor.ActorRef
import api.{ChatShoutboxMsg, ChatUserConnect, ChatUserDisconnect}
import models._
import models.mysql._
import utils._

case class ChatException(msg: String) extends Exception(msg)

private case class Session(user: User, var sockets: Set[ActorRef])

private case class Channel(var channel: ChatChannel, var members: Set[Int])

trait ChatService {
	def onlines: Set[Int]

	def connect(user: User, socket: ActorRef): Unit
	def disconnect(socket: ActorRef): Unit

	def loadShoutbox(): List[ChatMessage]
	def sendShoutbox(from: User, message: String): Unit

	def userInChannel(user: User, channel: Option[Int]): Boolean
	def fetchMessages(channel: Option[Int], select: Option[ChatSelect] = None): List[ChatMessage]

	def sendMessage(channel: Int, from: User, message: String): Try[ChatMessage]
	def sendWhisper(from: User, to: Int, message: String): Try[ChatWhisper]
}

class ChatServiceImpl extends ChatService {
	private var sessions = Map[Int, Session]()

	def onlines: Set[Int] = sessions.keySet

	def connect(user: User, socket: ActorRef): Unit = {
		sessions.get(user.id) match {
			case Some(session) =>
				session.sockets += socket

			case None =>
				sessions += user.id -> Session(user, Set(socket))
				Dispatcher !# ChatUserConnect(user.id)
		}
	}

	def disconnect(socket: ActorRef): Unit = {
		sessions.find {
			case (user, session) => session.sockets.contains(socket)
		} map {
			case (user, session) =>
				session.sockets -= socket
				if (session.sockets.size < 1) {
					sessions -= user
					Dispatcher !# ChatUserDisconnect(user)
				}
		}
	}

	private val shoutbox_backlog = LazyCache[List[ChatMessage]](1.minute) {
		DB.withSession { implicit s =>
			ChatMessages.filter(_.channel.isEmpty).sortBy(_.id.desc).take(100).list
		}
	}

	def loadShoutbox(): List[ChatMessage] = shoutbox_backlog

	def sendShoutbox(from: User, msg: String): Unit = {
		val message = DB.withSession { implicit s =>
			val template = ChatMessage(0, None, from.id, from.name, msg)
			val id = (ChatMessages returning ChatMessages.map(_.id)).insert(template)
			template.copy(id = id)
		}

		shoutbox_backlog := (message :: _)
		Dispatcher !# ChatShoutboxMsg(message)
	}

	private val memberships = LazyCollection[Int, Set[Int]](1.minute) { channel =>
		DB.withSession { implicit s =>
			ChatMembers.filter(_.channel === channel).map(_.user).list.toSet
		}
	}

	private implicit def unpackSelect(s: Option[ChatSelect]): ChatSelect = s.getOrElse(ChatSelect.all)

	def userInChannel(user: User, channel: Option[Int]): Boolean = channel match {
		case Some(cid) => memberships(cid).contains(user.id)
		case None => true
	}

	def fetchMessages(channel: Option[Int], select: Option[ChatSelect] = None): List[ChatMessage] = {
		DB.withSession { implicit s =>
			var query = select.toQuery
			channel match {
				case Some(cid) => query = query.filter(_.channel === cid)
				case None => query = query.filter(_.channel.isEmpty)
			}
			query.list
		}
	}

	def sendMessage(chanid: Int, from: User, message: String): Try[ChatMessage] = ???

	def sendWhisper(from: User, to: Int, message: String): Try[ChatWhisper] = ???
}
