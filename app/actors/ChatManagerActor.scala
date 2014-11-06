package actors

import actors.ChatManagerActor._
import akka.actor.{Actor, Props}
import api._
import gt.User
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.json._

object ChatManagerActor {
	val ChatManager = Akka.system.actorOf(Props[ChatManagerActor], name = "ChatManager")

	case class UserLogin(user: User)
	case class UserLogout(user: User)
	case class ListOnlines()

	case class SendWhisper(from: User, to: User, text: String)
}

class ChatManagerActor extends Actor {
	var onlines = Set[User]()

	def receive = {
		case UserLogin(user) if !onlines.contains(user) =>
			onlines foreach { _ ! Message("chat:onlines:update", Json.obj("type" -> "online", "user" -> user.id)) }
			onlines += user

		case UserLogout(user) if onlines.contains(user) =>
			onlines -= user
			onlines foreach { _ ! Message("chat:onlines:update", Json.obj("type" -> "offline", "user" -> user.id)) }

		case ListOnlines => sender ! onlines.map(_.id)

		case SendWhisper(from, to , text) =>
	}
}
