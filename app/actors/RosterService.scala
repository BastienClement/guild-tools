package actors

import scala.compat.Platform
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import actors.Actors.{Dispatcher, _}
import api._
import gt.Global.ExecutionContext
import models._
import models.mysql._
import play.api.libs.json._
import utils.LazyCache

trait RosterService {
	def users: Map[Int, User]
	def chars: Map[Int, Char]

	def user(id: Int): Option[User]
	def char(id: Int): Option[Char]

	def compositeRoster: JsObject
	def compositeUser(id: Int): JsObject

	def updateChar(char: Char): Unit
	def deleteChar(id: Int): Unit

	def refreshChar(id: Int): Unit
}

class RosterServiceImpl extends RosterService {
	/**
	 * List of every users
	 */
	val roster_users = LazyCache(1.minute) {
		DB.withSession { implicit s =>
			Users.filter(_.group inSet AuthenticatorHelper.allowedGroups).list.map(u => u.id -> u).toMap
		}
	}

	/**
	 * List of every chars
	 */
	val roster_chars = LazyCache(1.minute) {
		DB.withSession { implicit s =>
			Chars.list.map(c => c.id -> c).toMap
		}
	}

	/**
	 * The composite (users ++ chars)
	 */
	def roster_composite = LazyCache(1.minute) {
		Json.obj("users" -> roster_users.values.toList, "chars" -> roster_chars.values.toList)
	}

	/**
	 * Locks for currently updated chars
	 */
	var inflightUpdates = Set[Int]()

	def users: Map[Int, User] = roster_users
	def chars: Map[Int, Char] = roster_chars

	def user(id: Int): Option[User] = roster_users.get(id)
	def char(id: Int): Option[Char] = roster_chars.get(id)

	def compositeRoster: JsObject = roster_composite

	def compositeUser(id: Int): JsObject = {
		val chars = roster_chars.collect({ case (_, char) if char.owner == id => char }).toSet
		Json.obj("user" -> roster_users.get(id), "chars" -> chars)
	}

	def updateChar(char: Char): Unit = {
		// Update the cached object
		if (roster_chars.contains(char.id))
			roster_chars := (_.updated(char.id, char))
		else
			roster_chars := (_ + (char.id -> char))

		roster_composite.clear()
		inflightUpdates -= char.id
		Dispatcher !# RosterCharUpdate(char)
	}

	def deleteChar(id: Int): Unit = {
		roster_chars := roster_chars - id
		roster_composite.clear()
		Dispatcher !# RosterCharDelete(id)
	}

	def refreshChar(id: Int): Unit = {
		if (inflightUpdates.contains(id)) return
		val char_query = Chars.filter(_.id === id)

		DB.withSession { implicit s =>
			for (char <- char_query.firstOption if Platform.currentTime - char.last_update > 3600000) {
				inflightUpdates += char.id
				BattleNet.fetchChar(char.server, char.name) onComplete {
					case Success(nc) =>
						DB.withSession { implicit s =>
							char_query.map { c =>
								(c.klass, c.race, c.gender, c.level, c.achievements, c.thumbnail, c.ilvl, c.last_update)
							} update {
								(nc.clazz, nc.race, nc.gender, nc.level, nc.achievements, nc.thumbnail, nc.ilvl, Platform.currentTime)
							}

							RosterService.updateChar(char_query.first)
						}

					case Failure(_) =>
						DB.withSession { implicit s =>
							char_query.map(_.invalid).update(true)
							RosterService.updateChar(char_query.first)
						}
				}
			}
		}
	}
}
