package actors

import actors.RosterManager._
import akka.actor.{Actor, Props}
import api._
import gt.Socket
import models._
import models.mysql._
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.json._
import utils.LazyCell

import scala.concurrent.duration._

object RosterManager {
	val RosterManagerRef = Akka.system.actorOf(Props[RosterManager], name = "RosterManager")

	case class QueryUsers()
	case class QueryChars()
	case class QueryUser(id: Int)
	case class QueryChar(id: Int)

	case class Api_ListRoster()
	case class Api_QueryUser(id: Int)

	case class CharUpdate(char: Char)
	case class CharDelete(id: Int)
}

class RosterManager extends Actor {
	/**
	 * List of every users
	 */
	val roster_users = LazyCell(1.minute) {
		DB.withSession { implicit s =>
			Users.filter(_.group inSet AuthHelper.allowedGroups).list.map(u => u.id -> u).toMap
		}
	}

	/**
	 * List of every chars
	 */
	val roster_chars = LazyCell(1.minute) {
		DB.withSession { implicit s =>
			Chars.list.map(c => c.id -> c).toMap
		}
	}

	/**
	 * The composite (users ++ chars)
	 */
	def roster_composite = LazyCell(1.minute) {
		Json.obj("users" -> roster_users.values.toList, "chars" -> roster_chars.values.toList)
	}

	/**
	 * Handle messages
	 */
	def receive = {
		case QueryUsers => sender ! roster_users.get
		case QueryChars => sender ! roster_chars.get
		case QueryUser(id) => sender ! roster_users.get(id)
		case QueryChar(id) => sender ! roster_chars.get(id)

		case Api_ListRoster => sender ! roster_composite.get
		case Api_QueryUser(id) => {
			val chars = roster_chars.collect({ case (_, char) if char.owner == id => char }).toSet
			sender ! Json.obj("user" -> roster_users.get(id), "chars" -> chars)
		}

		case CharUpdate(char) => {
			if (roster_chars.contains(char.id))
				roster_chars := roster_chars.updated(char.id, char)
			else
				roster_chars := roster_chars.get + (char.id -> char)

			roster_composite.clear()
			Socket ! Message("roster:char:update", char)
		}

		case CharDelete(id) => {
			roster_chars := roster_chars - id

			roster_composite.clear()
			Socket ! Message("roster:char:delete", id)
		}
	}
}
