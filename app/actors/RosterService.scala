package actors

import actors.Actors.{ActorImplicits, BattleNet => Bnet}
import actors.BattleNet.BnetFailure
import actors.RosterService.CharUpdate
import gt.Global.ExecutionContext
import models._
import models.mysql._
import utils.{LazyCache, LazyCollection, PubSub}

import scala.compat.Platform
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success

object RosterService {
	case class CharUpdate(char: Char)
	case class CharDeleted(id: Int)
}

trait RosterService extends PubSub[User] with ActorImplicits {
	private val users = LazyCache(1.minute) {
		val q = Users filter (_.group inSet AuthService.allowedGroups)
		val l = q.run.await map (u => u.id -> u)
		l.toMap
	}

	private val outroster_users = LazyCollection[Int, Option[User]](15.minutes) { id =>
		Users.filter(_.id === id).headOption.await
	}

	private val owner_chars = LazyCollection[Int, Seq[Char]](1.minutes) { owner =>
		Chars.filter(_.owner === owner).sortBy(c => (c.main.desc, c.active.desc, c.level.desc, c.ilvl.desc)).run.await
	}

	private var inflightUpdates = Map[Int, Future[Char]]()

	def user(id: Int): Future[User] = users.get(id) orElse outroster_users(id)
	def chars(owner: Int): Future[Seq[Char]] = owner_chars(owner)

	def getChar(id: Int, user: Option[User]) = {
		val char = for (c <- Chars if c.id === id) yield c
		user match {
			case Some(u) => char filter (_.owner === u.id)
			case None => char
		}
	}

	/**
	 * Fetch a character from Battle.net and update its cached value in DB
	 */
	def refreshChar(id: Int, user: Option[User] = None): Future[Char] = {
		// Ensure we dont start two update at the same time
		if (inflightUpdates.contains(id)) inflightUpdates(id)
		else {
			// Request for the updated char
			val char = getChar(id, user)
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

	def promoteChar(id: Int, user: Option[User] = None): Unit = {
		for {
			Some(new_main) <- getChar(id, user).headOption
			Some(old_main) <- Chars.filter(char => char.main === true && char.owner === new_main.owner).headOption
		} {
			val a = (for {
				new_updated <- Chars.filter(char => char.id === new_main.id && char.main === false).map(_.main).update(true) if new_updated == 1
				old_updated <- Chars.filter(char => char.id === old_main.id && char.main === true).map(_.main).update(false) if old_updated == 1
			} yield ()).transactionally

			for {
				res <- a.run
				n <- Chars.filter(_.id === new_main.id).head
				o <- Chars.filter(_.id === old_main.id).head
			} {
				owner_chars.clear(n.owner)
				this !# CharUpdate(n)
				this !# CharUpdate(o)
			}
		}
	}

	private def changeEnabledState(id: Int, user: Option[User], state: Boolean): Unit = {
		for {
			char_query <- getChar(id, user)
			update <- char_query map (_.active) update (state)
			updated <- update.run if updated > 0
			char <- char_query.head
		} {
			owner_chars.clear(char.owner)
			this !# CharUpdate(char)
		}
	}

	def enableChar(id: Int, user: Option[User] = None): Unit = changeEnabledState(id, user, true)
	def disableChar(id: Int, user: Option[User] = None): Unit = changeEnabledState(id, user, false)

	def removeChar(id: Int, user: Option[User] = None): Unit = {
	}
}

class RosterServiceImpl extends RosterService
