package actors

import scala.concurrent.duration._
import scala.util.{Success, Try}
import api._
import models._
import models.mysql._
import play.api.libs.json._
import utils.{LazyCache, SmartTimestamp}

case class ChatException(msg: String) extends Exception(msg)

case class ChatManagerChannel(var channel: ChatChannel, var members: Set[Int]) {

}

trait ChatManager {
	def userLogin(user: User): Unit
	def userLogout(user: User): Unit

	def onlinesUsers: Set[Int]

	def sendMessage(channel: Option[Int], from: User, message: String): Try[ChatMessage]
	def sendWhisper(from: User, to: Int, message: String): Try[ChatWhisper]
}

class ChatManagerImpl extends ChatManager {
	var onlines = Map[Int, User]()

	val channels = LazyCache[Map[Int, ChatManagerChannel]](5.minutes) {
		DB.withSession { implicit s =>
			val query = for {
				channel <- ChatChannels
				membership <- ChatMembers if membership.channel === channel.id
			} yield (channel, membership.user)

			val res: List[(ChatChannel, Int)] = query.list
			res.groupBy(_._1.id).mapValues { list =>
				val channel = list(0)._1
				ChatManagerChannel(channel, list.map(_._2).toSet)
			}
		}
	}

	def userLogin(user: User): Unit = if (!onlines.contains(user.id)) {
		//for (u <- onlines.values) u ! Message("chat:onlines:update", Json.obj("type" -> "online", "user" -> user.id))
		onlines += (user.id -> user)
	}

	def userLogout(user: User): Unit = if (onlines.contains(user.id)) {
		onlines -= user.id
		//for (u <- onlines.values) u ! Message("chat:onlines:update", Json.obj("type" -> "offline", "user" -> user.id))
	}

	def onlinesUsers: Set[Int] = onlines.keySet

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
