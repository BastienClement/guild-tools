package actors

import api._
import gt.User
import play.api.libs.json._

trait ChatManagerInterface {
	def userLogin(user: User): Unit
	def userLogout(user: User): Unit

	def onlinesUsers: Set[Int]
}

class ChatManager extends ChatManagerInterface {
	var onlines = Map[Int, User]()

	def userLogin(user: User): Unit = if (!onlines.contains(user.id)) {
		for (u <- onlines.values) u ! Message("chat:onlines:update", Json.obj("type" -> "online", "user" -> user.id))
		onlines += (user.id -> user)
	}

	def userLogout(user: User): Unit = if (onlines.contains(user.id)) {
		onlines -= user.id
		for (u <- onlines.values) u ! Message("chat:onlines:update", Json.obj("type" -> "offline", "user" -> user.id))
	}

	def onlinesUsers: Set[Int] = onlines.keySet
}
