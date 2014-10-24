package actors

import akka.actor.Actor
import play.api.libs.concurrent.Akka
import play.api.Play.current
import utils.scheduler
import akka.actor.Props
import scala.concurrent.duration._
import gt.Global.ExecutionContext
import scala.concurrent.Future
import RosterManager._
import api._
import play.api.libs.json._
import play.api.libs.json.Json.JsValueWrapper
import models._
import models.mysql._
import java.util.Date
import gt.Socket

object RosterManager {
	val RosterManagerRef = Akka.system.actorOf(Props[RosterManager], name = "RosterManager")

	case class RefreshRoster()

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
	 * Refresh roster data
	 */
	scheduler.schedule(60.second, 60.second) {
		self ! RefreshRoster
	}

	/**
	 * List of every users
	 */
	var roster_users: Map[Int, User] = Map()

	/**
	 * List of every chars
	 */
	var roster_chars: Map[Int, Char] = Map()

	/**
	 * The composite (users ++ chars) [lazy]
	 */
	var roster_composite_val: Option[JsObject] = None
	def roster_composite_lazy = {
		val col = Json.obj("users" -> roster_users.values.toList, "chars" -> roster_chars.values.toList)
		roster_composite_val = Some(col)
		col
	}

	def roster_composite = {
		if (roster_composite_val.isDefined)
			roster_composite_val.get
		else {
			roster_composite_lazy
		}
	}

	/**
	 * Refresh the roster data from DB
	 */
	def refresh() = DB.withSession { implicit s =>
		roster_users = Users.filter(_.group inSet AuthHelper.allowedGroups).list.map(u => u.id -> u).toMap
		roster_chars = Chars.list.map(c => c.id -> c).toMap
		roster_composite_val = None
	}

	refresh();

	/**
	 * Update one char entry
	 */
	def updateChar(char: Char) = {
		roster_composite_val = None
		if (roster_chars.contains(char.id))
			roster_chars = roster_chars.updated(char.id, char)
		else
			roster_chars += (char.id -> char)
		Socket ! Message("roster:char:update", char)
	}

	/**
	 * Handle messages
	 */
	def receive = {
		case RefreshRoster => refresh()

		case QueryUsers => sender ! roster_users
		case QueryChars => sender ! roster_chars
		case QueryUser(id) => sender ! roster_users.get(id)
		case QueryChar(id) => sender ! roster_chars.get(id)

		case Api_ListRoster => sender ! roster_composite
		case Api_QueryUser(id) => {
			val chars = roster_chars.collect({ case (_, char) if (char.owner == id) => char }).toSet
			sender ! Json.obj("user" -> roster_users.get(id), "chars" -> chars)
		}

		case CharUpdate(char) => {
			roster_composite_val = None
			if (roster_chars.contains(char.id))
				roster_chars = roster_chars.updated(char.id, char)
			else
				roster_chars += (char.id -> char)
			Socket ! Message("roster:char:update", char)
		}

		case CharDelete(id) => {
			roster_composite_val = None
			roster_chars -= id
			Socket ! Message("roster:char:delete", id)
		}
	}
}