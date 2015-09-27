package actors

import actors.Actors.Implicits._
import models._
import models.mysql._
import utils.{LazyCache, LazyCollection, PubSub}

import scala.concurrent.Future
import scala.concurrent.duration._

object RosterService {
	case class CharUpdate(char: Char)
	case class CharDeleted(id: Int)
}

trait RosterService extends PubSub[User] {
	private val users = LazyCache(1.minute) {
		val q = Users filter (_.group inSet AuthService.allowedGroups)
		val l = q.run.await map (u => u.id -> u)
		l.toMap
	}

	private val outroster_users = LazyCollection[Int, Option[User]](15.minutes) { id =>
		Users.filter(_.id === id).headOption.await
	}

	private val owner_chars = LazyCollection[Int, Seq[Char]](1.minutes) { owner =>
		Chars.filter(_.owner === owner).run.await
	}

	private var inflightUpdates = Set[Int]()

	def user(id: Int): Future[User] = users.get(id) orElse outroster_users(id)
	def chars(owner: Int): Future[Seq[Char]] = owner_chars(owner)

	/*def updateChar(char: Char): Unit = {
		// Update the cached object
		owner_chars.clear(char.owner)

		inflightUpdates -= char.id
		this !# CharUpdate(char)
	}

	def deleteChar(id: Int): Unit = {
		roster_chars := roster_chars - id
		//Dispatcher !# RosterCharDelete(id)
	}*/

	/**
	 * Fetch a character from Battle.net and update its cached value in DB
	 */
	def refreshChar(id: Int): Unit = {
		/*if (inflightUpdates.contains(id)) return
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
		}*/
	}
}

class RosterServiceImpl extends RosterService
