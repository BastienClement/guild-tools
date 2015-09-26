package actors

import scala.compat.Platform
import scala.concurrent.duration._
import slick.jdbc.JdbcBackend
import scala.util.{Failure, Success, Try}
import actors.Actors.{Dispatcher, _}
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
		val q = Users filter (_.group inSet AuthService.allowedGroups)
		val l = q.run.await map (u => u.id -> u)
		l.toMap
	}

	/**
	 * List of every chars
	 */
	val roster_chars = LazyCache(1.minute) {
		val q = Chars filter (_.owner inSet roster_users.keySet)
		val l = q.run.await map (c => c.id -> c)
		l.toMap
	}

	/**
	 * Allow query of out-roster users as fallback
	 */
	def outroster_user = LazyCollection[Int, Option[User]](15.minutes) { id =>
		Users.filter(_.id === id).headOption.await
	}

	/**
	 * Allow query of out-roster chars as fallback
	 */
	def outroster_chars = LazyCollection[Int, Set[Char]](15.minutes) { owner =>
		Chars.filter(_.owner === owner).run.await.toSet
	}

	/**
	 * Allow query of a specific out-roster char
	 */
	def outroster_char = LazyCollection[Int, Option[Char]](15.minutes) { id =>
		Chars.filter(_.id === id).headOption.await
	}

	/**
	 * Locks for currently updated chars
	 */
	var inflightUpdates = Set[Int]()

	def users: Map[Int, User] = roster_users
	def chars: Map[Int, Char] = roster_chars

	def user(id: Int): Option[User] = roster_users.get(id) orElse outroster_user(id)
	def char(id: Int): Option[Char] = roster_chars.get(id) orElse outroster_char(id)

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

		inflightUpdates -= char.id
		//Dispatcher !# RosterCharUpdate(char)
	}

	def deleteChar(id: Int): Unit = {
		roster_chars := roster_chars - id
		//Dispatcher !# RosterCharDelete(id)
	}

	/**
	 * Fetch a character from Battle.net and update its cached value in DB
	 */
	def refreshChar(id: Int): Unit = {
		if (inflightUpdates.contains(id)) return
		val char_query = Chars.filter(_.id === id)

		for (char <- char_query.headOption.await if Platform.currentTime - char.last_update > 1800000) {
			inflightUpdates += char.id
			BattleNet.fetchChar(char.server, char.name) onComplete handleResult(char)
		}

		// Handle Battle.net response, both success and failure
		def handleResult(char: Char)(res: Try[Char]): Unit = {
			res match {
				case Success(new_char) => handleSuccess(new_char, char)
				case Failure(e) => handleFailure(e, char)
			}

			RosterService.updateChar(char_query.head.await)
		}

		// Handle a sucessful char retrieval
		def handleSuccess(nc: Char, char: Char): Unit = {
			char_query.map { c =>
				(c.klass, c.race, c.gender, c.level, c.achievements, c.thumbnail, c.ilvl, c.failures, c.invalid, c.last_update)
			} update {
				(nc.clazz, nc.race, nc.gender, nc.level, nc.achievements, nc.thumbnail, math.max(nc.ilvl, char.ilvl), 0, false, Platform.currentTime)
			}
		}

		// Handle an error on char retrieval
		def handleFailure(e: Throwable, char: Char): Unit = e match {
			// The character is not found on Battle.net, increment failures counter
			case BattleNetFailure(response) if response.status == 404 =>
				val failures = char_query.map(_.failures).head.await + 1
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
