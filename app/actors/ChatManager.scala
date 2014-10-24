package actors

import akka.actor.Actor
import play.api.libs.concurrent.Akka
import play.api.Play.current
import utils.scheduler
import akka.actor.Props
import scala.concurrent.duration._
import gt.Global.ExecutionContext
import scala.concurrent.Future
import gt.User
import ChatManager._
import api._
import play.api.libs.json._
import play.api.libs.json.Json.JsValueWrapper
import models._
import models.mysql._

object ChatManager {
	val ChatManagerRef = Akka.system.actorOf(Props[ChatManager], name = "ChatManager")

	case class UserLogin(user: User)
	case class UserLogout(user: User)
	case class ListOnlines()
}

class ChatManager extends Actor {
	var onlines = Set[User]()
	var memberships = Map[User, Set[ChatRoom]]()
	var rooms = Map[String, ChatRoom]()

	def receive = {
		case UserLogin(user) => {
			onlines foreach { _ ! Message("chat:onlines:update", Json.obj("type" -> "online", "user" -> user.id)) }
			onlines += user
		}

		case UserLogout(user) => {
			onlines -= user
			onlines foreach { _ ! Message("chat:onlines:update", Json.obj("type" -> "offline", "user" -> user.id)) }
		}

		case ListOnlines => sender ! onlines.map(_.id)
	}

	class ChatRoom(val id: String) {
		rooms += (id -> this)
		var members = Set[User]()

		def join(user: User) = {
			members += user
		}

		def leave(user: User) = {
			members -= user
		}

		def online(user: User) = {

		}

		def offline(user: User) = {

		}
	}
}