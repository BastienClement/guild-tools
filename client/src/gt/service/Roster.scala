package gt.service

import api.Roster.UserData
import boopickle.DefaultBasic._
import model.{Toon, User}
import scala.collection.mutable
import xuen.rx.{Rx, Var}

object Roster extends Service {
	/** The roster channel */
	private[this] val channel = registerChannel("roster", lzy = false)

	/** Creates a placeholder toon for the player's main toon */
	private def placeholderForMain(user: Int) = {
		Toon(-1, s"User#$user", "Unknown", user, true, true, 0, 0, 0, 0, 0, null, 0, "DPS", true, 0)
	}

	/** Creates a placeholder toon for a generic player toon */
	private def placeholderForToon(user: Int, toon: Int) = {
		Toon(-1, s"Toon#$toon", "Unknown", user, true, true, 0, 0, 0, 0, 0, null, 0, "DPS", true, 0)
	}

	/** Data about a player */
	private case class UserCache(userid: Int, data: Option[UserData] = None) {
		/** The user profile data */
		private[Roster] val userData = Var(null: User)

		/** The user main toon ID */
		private[Roster] val mainToonID = Var(-1)

		/** Public view of the user profile data */
		val user: Rx[User] = userData

		/** The player main toon */
		val main = Rx {
			if (mainToonID < 0) placeholderForMain(userid)
			else dataForToon.getOrElseUpdate(mainToonID, placeholderForToon(userid, mainToonID)).!
		}

		/**
		  * Loads a player template.
		  *
		  * This function is either called at initialization if a template is already
		  * available (init done by the load-roster call) or once the response to
		  * the load-user is received (init done by the client requesting an
		  * unknown user).
		  */
		private[Roster] def loadData(data: UserData): Unit = Rx.atomically {
			mainToonID := data.main.getOrElse(-1)
			userData := data.user
			for (toon <- data.toons) {
				ownerForToon.put(toon.id, data.user.id)
				dataForToon.get(toon.id) match {
					case Some(cache) => cache := toon
					case None => dataForToon.put(toon.id, Var(toon))
				}
			}
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

	/** A list of owner for each toon */
	private[this] val ownerForToon = mutable.Map[Int, Int]()

	/** Loads a received user data */
	private def loadUserData(data: UserData): Unit = {
		val id = data.user.id
		dataForUser.get(id) match {
			case Some(cache) => cache.loadData(data)
			case None => dataForUser.put(id, UserCache(id, Some(data)))
		}
	}

	/** Called when the service is enabled */
	override protected def enable(): Unit = {
		channel.request("load-roster") { datas: Iterable[UserData] =>
			Rx.atomically { datas.foreach(loadUserData) }
		}
	}

	def toon(toon: Int): Rx[Toon] = dataForToon(toon)
	def mainForUser(user: Int): Rx[Toon] = dataForUser(user).main
}
