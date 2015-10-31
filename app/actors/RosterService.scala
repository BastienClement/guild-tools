package actors

import actors.BattleNet.BnetFailure
import actors.RosterService.{CharDeleted, CharUpdated}
import models._
import models.mysql._
import reactive.ExecutionContext
import scala.compat.Platform
import scala.concurrent.Future
import scala.concurrent.duration._
import utils.{Cache, CacheCell, PubSub}

private[actors] class RosterServiceImpl extends RosterService

object RosterService extends StaticActor[RosterService, RosterServiceImpl]("RosterService") {
	case class CharUpdated(char: Char)
	case class CharDeleted(char: Char)
}

trait RosterService extends PubSub[User] {
	// Cache of in-roster users
	private val users = CacheCell.async[Map[Int, User]](1.minute) {
		val query = for {
			user <- Users if user.group inSet AuthService.allowed_groups
		} yield user.id -> user
		query.run.map(s => s.toMap)
	}

	// Cache of out-of-roster users
	private val outroster_users = Cache.async[Int, User](15.minutes) { id =>
		Users.filter(_.id === id).head
	}

	// List of chars for a roster member
	val roster_chars = CacheCell.async[Map[Int, Seq[Char]]](1.minutes) {
		val users = for (user <- Users if user.group inSet AuthService.allowed_groups) yield user.id
		val chars = for (char <- Chars.sortBy(c => (c.main.desc, c.active.desc, c.level.desc, c.ilvl.desc)) if char.owner.in(users)) yield char
		chars.run.map(_.groupBy(_.owner))
	}

	// List of chars for a specific user
	private val user_chars = Cache.async[Int, Seq[Char]](1.minutes) { owner =>
		Chars.filter(_.owner === owner).sortBy(c => (c.main.desc, c.active.desc, c.level.desc, c.ilvl.desc)).run
	}

	// List of pending Battle.net update
	// Two b.net update on the same char at the same time will produce the same shared future
	// resolved at a later time with the same char
	private var inflightUpdates = Map[Int, Future[Char]]()

	// Request a list of user from the roster
	def roster_users: Future[Iterable[User]] = for (u <- users.value) yield u.values

	// Request user informations
	// This function query both the full roster cache or the out-of-roster generator
	def user(id: Int): Future[User] = users.value.map(m => m(id)) recoverWith { case _ => outroster_users(id) }

	// Request a list of chars for a specific owner
	def chars(owner: Int): Future[Seq[Char]] = roster_chars.value.map(c => c(owner)) recoverWith { case _ => user_chars(owner) }

	// Construct a request for a char with a given id.
	// If user is defined, also ensure that the char is owned by the user
	private def getOwnChar(id: Int, user: Option[User]) = {
		val query = for (c <- Chars if c.id === id) yield c
		user.map(u => query.filter(_.owner === u.id)).getOrElse(query)
	}

	// Notify subscriber that a char has been updated
	// Also clear the local cache for the owner of that char
	private def notifyUpdate(char: Char): Char = {
		user_chars.clear(char.owner)
		roster_chars.clear()
		this !# CharUpdated(char)
		char
	}

	// Fetch a character from Battle.net and update its cached value in DB
	// TODO: refactor
	def refreshChar(id: Int, user: Option[User] = None): Future[Char] = {
		// Ensure we dont start two update at the same time
		if (inflightUpdates.contains(id)) inflightUpdates(id)
		else {
			// Request for the updated char
			val char = getOwnChar(id, user)
			val res =
				char.filter(c => c.last_update < Platform.currentTime - (1000 * 60 * 15)).head recover {
					case cause => throw new Exception("Cannot refresh character at this time", cause)
				} flatMap { oc =>
					BattleNet.fetchChar(oc.server, oc.name) recoverWith {
						case BnetFailure(response) if response.status == 404 =>
							char.map(_.failures).head map { f =>
								val failures = f + 1
								val invalid = failures >= 3
								char map {
									c => (c.active, c.failures, c.invalid, c.last_update)
								} update {
									(oc.active && (!invalid || oc.main), failures, invalid, Platform.currentTime)
								}
							} flatMap { query =>
								query.run map {
									_ => throw new Exception("Battle.net Error: " + response.body)
								}
							}

						// Another error occured, just update the last_update, but don't count as failure
						case cause =>
							val query = char map { c => c.last_update } update Platform.currentTime
							query.run map {
								_ => throw new Exception("Error while updating character", cause)
							}
					} map { nc =>
						char map {
							c => (c.klass, c.race, c.gender, c.level, c.achievements, c.thumbnail, c.ilvl, c.failures, c.invalid, c.last_update)
						} update {
							(nc.clazz, nc.race, nc.gender, nc.level, nc.achievements, nc.thumbnail, math.max(nc.ilvl, oc.ilvl), 0, false, Platform.currentTime)
						}
					} flatMap {
						query => query.run
					} flatMap {
						_ => char.head
					}
				}

			res andThen {
				case _ => inflightUpdates -= id
			} recoverWith {
				case _ => char.head
			} foreach {
				updated => notifyUpdate(updated)
			}

			inflightUpdates += id -> res
			res
		}
	}

	// Promote a new char as the main for the user
	def promoteChar(id: Int, user: Option[User] = None): Future[(Char, Char)] = DB.run {
		// Construct the update for a specific char
		def update_char(id: Int, main: Boolean) =
			Chars.filter(char => char.id === id && char.main === !main).map(_.main).update(main)

		val query = for {
			// Fetch new and old main chars
			new_main <- getOwnChar(id, user).result.head
			old_main <- Chars.filter(char => char.main === true && char.owner === new_main.owner).result.head

			// Update them
			new_updated <- update_char(new_main.id, true)
			old_updated <- update_char(old_main.id, false)

			// Ensure we have updated two chars
			_ = if (new_updated + old_updated == 2) () else throw new Exception("Failed to update chars")
		} yield {
			(notifyUpdate(new_main.copy(main = true)), notifyUpdate(old_main.copy(main = false)))
		}

		query.transactionally
	}

	// Common database query for enableChar() and disableChar()
	private def changeEnabledState(id: Int, user: Option[User], state: Boolean): Future[Char] = DB.run {
		val char_query = getOwnChar(id, user).filter(_.main === false)
		for {
			_ <- char_query.map(_.active).update(state)
			char <- char_query.result.head
		} yield {
			this.notifyUpdate(char)
		}
	}

	// Update the enabled state of a character
	def enableChar(id: Int, user: Option[User] = None): Future[Char] = changeEnabledState(id, user, true)
	def disableChar(id: Int, user: Option[User] = None): Future[Char] = changeEnabledState(id, user, false)

	def changeRole(id: Int, role: String, user: Option[User] = None): Future[Char] = DB.run {
		if (!Chars.validateRole(role)) throw new Exception("Invalid role")
		val char_query = getOwnChar(id, user)
		for {
			_ <- char_query.filter(_.role =!= role).map(_.role).update(role)
			char <- char_query.result.head
		} yield {
			this.notifyUpdate(char)
		}
	}

	// Remove an existing character from the database
	def removeChar(id: Int, user: Option[User] = None): Future[Char] = DB.run {
		for {
			char <- getOwnChar(id, user).filter(c => c.main === false && c.active === false).result.head
			count <- Chars.filter(c => c.id === char.id).delete
			_ = if (count > 0) () else throw new Exception("Failed to delete this character")
		} yield {
			user_chars.clear(char.owner)
			this !# CharDeleted(char)
			char
		}
	}

	// Add a new character for a specific user
	def registerChar(server: String, name: String, owner: Int, role: Option[String]): Future[Char] = {
		for {
			char <- BattleNet.fetchChar(server, name)
			res <- registerChar(char, owner, role)
		} yield {
			res
		}
	}

	def registerChar(char: Char, owner: Int, role: Option[String]): Future[Char] = DB.run {
		val count_main = Chars.filter(c => c.owner === owner && c.main === true).size.result
		val query = for {
			// Count the number of main for this user
			pre_count <- count_main
			// Construct the char for insertion with correct role, owner and main flag
			insert_char = char.copy(role = role.getOrElse(char.role), owner = owner, main = pre_count < 1)
			// Insert this char
			insert_id <- (Chars returning Chars.map(_.id)) += insert_char
			// Ensure we have exactly one main for this user now
			post_count <- count_main
		} yield {
			if (post_count != 1) throw new Exception("Failed to register new character")
			val final_char = insert_char.copy(id = insert_id)
			this.notifyUpdate(final_char)
			final_char
		}
		query.transactionally
	}
}
