package actors

import gtp3.{Channel, Payload}
import models._
import play.api.libs.json._
import utils.ChannelList

import scala.language.implicitConversions

private case class ChatSession(user: User, var away: Boolean, var channels: Map[gtp3.Channel, Boolean])

trait ChatService {
	def onlines: Map[Int, Boolean]

	def connect(channel: gtp3.Channel): Unit
	def disconnect(channel: gtp3.Channel): Unit
	def setAway(channel: gtp3.Channel, away: Boolean): Unit

	/*def loadShoutbox(): List[ChatMessage]
	def sendShoutbox(from: User, message: String): Unit

	def userInChannel(user: User, channel: Option[Int]): Boolean
	def fetchMessages(channel: Option[Int], select: Option[ChatSelect] = None): List[ChatMessage]

	def sendMessage(channel: Int, from: User, message: String): Try[ChatMessage]
	def sendWhisper(from: User, to: Int, message: String): Try[ChatWhisper]*/
}

class ChatServiceImpl extends ChatService with ChannelList[ChatSession] {
	private var sessions = Map[Int, ChatSession]()

	def onlines: Map[Int, Boolean] = sessions.map {
		case (user, session) => user -> session.away
	}

	private def updateAway(session: ChatSession) = {
		val away = session.channels.values.forall(away => away)
		if (away != session.away) {
			session.away = away
			broadcast("away-state-changed", Json.arr(session.user.id, away))
		}
	}

	def connect(channel: Channel): Unit = {
		val user = channel.socket.user
		val chan = channel -> false

		sessions.get(user.id) match {
			case Some(session) =>
				registerChannel(channel, session)
				session.channels += chan
				updateAway(session)

			case None =>
				val session = ChatSession(user, false, Map(chan))
				registerChannel(channel, session)
				sessions += user.id -> session
				broadcast("connected", user.id)
		}
	}

	def disconnect(channel: gtp3.Channel): Unit = {
		sessions.find {
			case (user, session) => session.channels.contains(channel)
		} foreach {
			case (user, session) =>
				session.channels -= channel
				if (session.channels.size < 1) {
					sessions -= user
					unregisterChannel(channel)
					updateAway(session)
					broadcast("disconnected", user)
				}
		}
	}

	def setAway(channel: gtp3.Channel, away: Boolean) = {
		for (session <- sessions.get(channel.socket.user.id)) {
			session.channels = session.channels.updated(channel, away)
			updateAway(session)
		}
	}

	/*
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
			//Dispatcher !# ChatShoutboxMsg(message)
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
		*/
}
