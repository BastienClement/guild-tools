package actors

import scala.concurrent.duration._
import scala.util.{Success, Try}
import actors.Actors.Dispatcher
import akka.actor.ActorRef
import api.{ChatUserConnect, ChatUserDisconnect}
import models._
import models.mysql._
import utils.{LazyCache, SmartTimestamp}

case class ChatException(msg: String) extends Exception(msg)

case class ChatSession(user: User, var sockets: Set[ActorRef])

case class ChatChannel(var channel: ChatChannel, var members: Set[Int]) {

}

trait Chat {
	def onlines: Set[Int]

	def connect(user: User, socket: ActorRef): Unit
	def disconnect(socket: ActorRef): Unit

	def sendMessage(channel: Option[Int], from: User, message: String): Try[ChatMessage]
	def sendWhisper(from: User, to: Int, message: String): Try[ChatWhisper]
}

class ChatImpl extends Chat {
	private var sessions = Map[Int, ChatSession]()

	val channels = LazyCache[Map[Int, ChatChannel]](5.minutes) {
		DB.withSession { implicit s =>
			val query = for {
				channel <- ChatChannels
				membership <- ChatMembers if membership.channel === channel.id
			} yield (channel, membership.user)

			val res: List[(ChatChannel, Int)] = query.list
			res.groupBy(_._1.id).mapValues { list =>
				val channel = list(0)._1
				ChatChannel(channel, list.map(_._2).toSet)
			}
		}
	}

	def connect(user: User, socket: ActorRef): Unit = {
		sessions.get(user.id) match {
			case Some(session) =>
				session.sockets += socket

			case None =>
				sessions += user.id -> ChatSession(user, Set(socket))
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

	def onlines: Set[Int] = sessions.keySet

	def sendMessage(chanid: Option[Int], from: User, message: String): Try[ChatMessage] = Try {
		// Load channel if specified
		val channel = for (id <- chanid) yield channels.value(id)

		// Check if user is in channel
		for (chan <- channel) {
			if (!chan.members.contains(from.id)) throw ChatException("User not in channel")
		}

		// Save message in database
		val msg = ChatMessage(0, chanid, Some(from.id), from.name, SmartTimestamp.now, message)
		DB.withSession { implicit s => ChatMessages.insert(msg) }

		// Broadcast message
		/*for {
			userid <- channel.map(_.members) getOrElse onlines.keySet
			user <- onlines.get(userid)
		} user ! Message("chat:message", msg)*/

		msg
	}

	def sendWhisper(from: User, to: Int, message: String): Try[ChatWhisper] = {
		val msg = ChatWhisper(0, from.id, to, SmartTimestamp.now, message)
		DB.withSession { implicit s => ChatWhispers.insert(msg) }

		Success(msg)
	}
}
