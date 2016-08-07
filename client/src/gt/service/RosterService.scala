package gt.service

import api.Roster.UserData
import boopickle.DefaultBasic._
import gt.service.base.{Delegate, Service}
import model.{Toon, User}
import scala.collection.mutable
import scala.concurrent.Future
import xuen.rx.{Rx, Var}

object RosterService extends Service with Delegate {
	/** The roster channel */
	private[this] val channel = registerChannel("roster", lzy = false)

	/** Creates a placeholder toon for the player's main toon */
	private def placeholderForMain(user: Int) = {
		Toon(-1, s"User#$user", "Unknown", user, true, true, 0, 0, 0, 0, 0, null, 0, "DPS", true, 0)
	}

	/** Creates a placeholder toon for a generic player toon */
	private def placeholderForToon(user: Int, toon: Int) = {
		Toon(-1, s"Toon#$toon", "Unknown", user, false, true, 0, 0, 0, 0, 0, null, 0, "DPS", true, 0)
	}

	/** Data about a player */
	private case class UserCache(userid: Int, data: Option[UserData] = None) {
		/** The user profile data */
		val userData = Var(User(userid, s"User#$userid", 0))

		/** The set of user's toons id */
		val toonsIds = Var(Set.empty[Int])

		/** The user main toon ID */
		private val mainToonID = Var(-1)

		/** The player main toon */
		val main = Rx {
			if (mainToonID < 0) placeholderForMain(userid)
			else dataForToon.getOrElseUpdate(mainToonID, placeholderForToon(userid, mainToonID)).!
		}

		/** The sorted list of toons */
		val toons = Rx {
			toonsIds.!.map { id => toon(id).! }.toSeq.sorted
		}

		/**
		  * Loads a player template.
		  *
		  * This function is either called at initialization if a template is already
		  * available (init done by the load-roster call) or once the response to
		  * the load-user is received (init done by the client requesting an
		  * unknown user).
		  */
		private[RosterService] def loadData(data: UserData): Unit = Rx.atomically {
			mainToonID := data.main.getOrElse(-1)
			userData := data.user
			data.toons.foreach(updateToon)
		}

		private[RosterService] def updateToon(toon: Toon): Unit = Rx.atomically {
			if (toon.main) mainToonID := toon.id
			toonsIds ~= (_ + toon.id)
			dataForToon.get(toon.id) match {
				case Some(cache) => cache := toon
				case None => dataForToon.put(toon.id, Var(toon))
			}
		}

		private[RosterService] def removeToon(toon: Toon): Unit = {
			toonsIds ~= (_ - toon.id)
			dataForToon.remove(toon.id).foreach(_.invalidate())
		}

		// Apply template is availble of request it
		data match {
			case Some(d) => loadData(d)
			case None => channel.request("load-user", userid).apply(loadData _)
		}
	}

	/** Cached data for each user */
	private[this] val dataForUser = mutable.Map[Int, UserCache]().withDefault(createDefaultUser)
	private def createDefaultUser(id: Int): UserCache = {
		val cache = UserCache(id)
		dataForUser.put(id, cache)
		cache
	}

	/** Cached data for each toon */
	private[this] val dataForToon = mutable.Map[Int, Var[Toon]]().withDefault(createDefaultToon)
	private def createDefaultToon(id: Int): Var[Toon] = {
		channel.request("load-user-toon", id).apply(loadUserData _)
		val toon = placeholderForToon(-1, id)
		dataForToon.put(id, toon)
		toon
	}

	/** Loads a received user data */
	private def loadUserData(data: UserData): Unit = {
		val id = data.user.id
		dataForUser.get(id) match {
			case Some(cache) => cache.loadData(data)
			case None => dataForUser.put(id, UserCache(id, Some(data)))
		}
	}

	def loadRoster(): Future[Unit] = {
		channel.request("load-roster") { datas: Iterable[UserData] =>
			Rx.atomically { datas.foreach(loadUserData) }
		}
	}

	def user(user: Int): Rx[User] = dataForUser(user).userData
	def toons(user: Int): Rx[Seq[Toon]] = dataForUser(user).toons
	def toon(toon: Int): Rx[Toon] = dataForToon(toon)
	def main(user: Int): Rx[Toon] = dataForUser(user).main

	def changeToonRole(tid: Int, role: String): Unit = channel.send("change-toon-role", (tid, role))
	def promoteToon(tid: Int): Unit = channel.send("promote-toon", tid)
	def enableToon(tid: Int): Unit = channel.send("enable-toon", tid)
	def disableToon(tid: Int): Unit = channel.send("disable-toon", tid)
	def removeToon(tid: Int): Unit = channel.send("remove-toon", tid)

	def updateToon(tid: Int): Future[Unit] = channel.request("update-toon", tid).as[Unit]

	message("toon-updated") { toon: Toon =>
		dataForUser.get(toon.owner) match {
			case Some(owner) => owner.updateToon(toon)
			case None => /* ignore */
		}
	}

	message("toon-deleted") { toon: Toon =>
		dataForUser.get(toon.owner) match {
			case Some(owner) => owner.removeToon(toon)
			case None => /* ignore */
		}
	}
}
