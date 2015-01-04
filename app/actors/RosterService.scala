package actors

import scala.compat.Platform
import scala.concurrent.duration._
import scala.slick.jdbc.JdbcBackend.SessionDef
import scala.util.{Failure, Success, Try}
import actors.Actors.{Dispatcher, _}
import api._
import gt.Global.ExecutionContext
import models._
import models.mysql._
import play.api.libs.json._
import utils.{LazyCollection, LazyCache}

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
			Users.filter(_.group inSet AuthService.allowedGroups).list.map(u => u.id -> u).toMap
		}
	}

	/**
	 * List of every chars
	 */
	val roster_chars = LazyCache(1.minute) {
		DB.withSession { implicit s =>
			Chars.filter(_.owner inSet roster_users.keySet).list.map(c => c.id -> c).toMap
		}
	}

	/**
	 * The composite (users ++ chars)
	 */
	def roster_composite = LazyCache(1.minute) {
		Json.obj("users" -> roster_users.values.toList, "chars" -> roster_chars.values.toList)
	}

	/**
	 * Allow query of out-roster users as fallback
	 */
	def outroster_user = LazyCollection[Int, Option[User]](15.minutes) { id =>
		DB.withSession { implicit s =>
			Users.filter(_.id === id).firstOption
		}
	}

	/**
	 * Allow query of out-roster chars as fallback
	 */
	def outroster_chars = LazyCollection[Int, Set[Char]](15.minutes) { owner =>
		DB.withSession { implicit s =>
			Chars.filter(_.owner === owner).list.toSet
		}
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
		roster_users.get(id) map { user =>
			// Attempt to fetch in-guild user and chars
			val chars = roster_chars.collect({ case (_, char) if char.owner == id => char }).toSet
			Json.obj("user" -> user, "chars" -> chars)
		} orElse {
			// Fallback if user is no longer in guild
			outroster_user.get(id) map { user =>
				val chars = outroster_chars.get(id)
				Json.obj("user" -> user, "chars" -> chars, "outofroster" -> true)
			}
		} getOrElse {
			// The user doesn't seem to exist at all
			Json.obj("user" -> JsNull, "chars" -> JsNull)
		}
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

	/**
	 * Fetch a character from Battle.net and update its cached value in DB
	 */
	def refreshChar(id: Int): Unit = {
		if (inflightUpdates.contains(id)) return
		val char_query = Chars.filter(_.id === id)

		DB.withSession { implicit s =>
			for (char <- char_query.firstOption if Platform.currentTime - char.last_update > 1800000) {
				inflightUpdates += char.id
				BattleNet.fetchChar(char.server, char.name) onComplete handleResult(char)
			}
		}

		// Handle Battle.net response, both success and failure
		def handleResult(char: Char)(res: Try[Char]): Unit = {
			DB.withSession { implicit s =>
				res match {
					case Success(new_char) => handleSuccess(new_char, char)
					case Failure(e) => handleFailure(e, char)
				}

				RosterService.updateChar(char_query.first)
			}
		}

		// Handle a sucessful char retrieval
		def handleSuccess(nc: Char, char: Char)(implicit s: SessionDef): Unit = {
			char_query.map { c =>
				(c.klass, c.race, c.gender, c.level, c.achievements, c.thumbnail, c.ilvl, c.failures, c.invalid, c.last_update)
			} update {
				(nc.clazz, nc.race, nc.gender, nc.level, nc.achievements, nc.thumbnail, math.max(nc.ilvl, char.ilvl), 0, false, Platform.currentTime)
			}
		}

		// Handle an error on char retrieval
		def handleFailure(e: Throwable, char: Char)(implicit s: SessionDef): Unit = e match {
			// The character is not found on Battle.net, increment failures counter
			case BattleNetFailure(response) if response.status == 404 =>
				val failures = char_query.map(_.failures).first + 1
				val invalid = failures >= 3

				char_query.map { c =>
					(c.active, c.failures, c.invalid, c.last_update)
				} update {
					(char.active && (!invalid || char.main), failures, invalid, Platform.currentTime)
				}

			// Another error occured, just update the update date, but don't count as failure
			case _ =>
				char_query.map(_.last_update).update(Platform.currentTime)
		}
	}
}
