package actors

import actors.Actors.{ActorImplicits, BattleNet => Bnet}
import actors.BattleNet.BnetFailure
import actors.RosterService.CharUpdate
import gt.Global.ExecutionContext
import models._
import models.mysql._
import utils.{CacheCell, Cache, PubSub}

import scala.compat.Platform
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Try, Success}

object RosterService {
	case class CharUpdate(char: Char)
	case class CharDeleted(id: Int)
}

trait RosterService extends PubSub[User] with ActorImplicits {
	private val users = CacheCell(1.minute) {
		val q = Users filter (_.group inSet AuthService.allowedGroups)
		val l = q.run.await map (u => u.id -> u)
		l.toMap
	}

	private val outroster_users = Cache[Int, Option[User]](15.minutes) { id =>
		Users.filter(_.id === id).headOption.await
	}

	// List of chars for a specific user
	private val owner_chars = Cache[Int, Seq[Char]](1.minutes) { owner =>
		Chars.filter(_.owner === owner).sortBy(c => (c.main.desc, c.active.desc, c.level.desc, c.ilvl.desc)).run.await
	}

	// List of pending Battle.net update
	// Two b.net update on the same char at the same time will produce the same shared future
	// resolved at a later time with the same char
	private var inflightUpdates = Map[Int, Future[Char]]()

	def user(id: Int): Future[User] = users.get(id) orElse outroster_users(id)
	def chars(owner: Int): Future[Seq[Char]] = owner_chars(owner)

	private def getOwnChar(id: Int, user: Option[User]) = {
		val char = for (c <- Chars if c.id === id) yield c
		user match {
			case Some(u) => char filter (_.owner === u.id)
			case None => char
		}
	}

	private def charUpdated(char: Char): Unit = {
		owner_chars.clear(char.owner)
		this !# CharUpdate(char)
	}

	/**
	 * Fetch a character from Battle.net and update its cached value in DB
	 */
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
					Bnet.fetchChar(oc.server, oc.name) recoverWith {
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
							val query = char map { c => c.last_update } update (Platform.currentTime)
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
				updated =>
					owner_chars.clear(updated.owner)
					this !# CharUpdate(updated)
			}

			inflightUpdates += id -> res
			res
		}
	}

	// Promote a new char as the main for the user
	def promoteChar(id: Int, user: Option[User] = None): Future[Unit] = {
		for {
			new_main <- getOwnChar(id, user).head
			old_main <- Chars.filter(char => char.main === true && char.owner === new_main.owner).head
		} yield {
			def update_char(id: Int, main: Boolean) =
				Chars.filter(char => char.id === id && char.main === !main).map(_.main).update(main)

			val update = (for {
				new_updated <- update_char(new_main.id, true)
				old_updated <- update_char(old_main.id, false)
				res = if (new_updated + old_updated == 2) () else throw new Exception("Failed to update chars")
			} yield res).transactionally

			for {
				res <- update.run
				n <- Chars.filter(_.id === new_main.id).head
				o <- Chars.filter(_.id === old_main.id).head
			} yield {
				owner_chars.clear(n.owner)
				this !# CharUpdate(n)
				this !# CharUpdate(o)
			}
		}
	}

	private def changeEnabledState(id: Int, user: Option[User], state: Boolean): Future[Unit] = {
		val char_query = getOwnChar(id, user)

		val update = for {
			updated <- char_query.map(_.active).update(state)
			_ = if (updated == 1) () else throw new Exception("Failed to update character active state")
			char <- char_query.result.head
		} yield {
			this.charUpdated(char)
			()
		}

		DB.run(update)
	}

	def enableChar(id: Int, user: Option[User] = None): Future[Unit] = changeEnabledState(id, user, true)
	def disableChar(id: Int, user: Option[User] = None): Future[Unit] = changeEnabledState(id, user, false)

	def removeChar(id: Int, user: Option[User] = None): Future[Unit] = {
	}
}

class RosterServiceImpl extends RosterService
