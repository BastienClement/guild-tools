package gt.service

import boopickle.DefaultBasic._
import model.Toon
import scala.collection.mutable
import xuen.rx.{Rx, Var}

object Roster extends Service {
	val channel = registerChannel("roster", lzy = false)

	override protected def enable(): Unit = {
		channel.request("load-roster")
	}

	private def placeholderForMain(user: Int) = {
		Toon(-1, s"User#$user", "Unknown", user, true, true, 0, 0, 0, 0, 0, null, 0, "DPS", true, 0)
	}

	private def placeholderForToon(user: Int, toon: Int) = {
		Toon(-1, s"Toon#$toon", "Unknown", user, true, true, 0, 0, 0, 0, 0, null, 0, "DPS", true, 0)
	}

	case class UserData(user: Int) {
		channel.request("load-user-infos", user)

		private[Roster] val mainToonID: Var[Int] = -1

		val main = Rx {
			if (mainToonID < 0) placeholderForMain(user)
			else dataForToon.getOrElseUpdate(mainToonID, placeholderForToon(user, mainToonID)).!
		}
	}

	private[this] val dataForUser = mutable.Map[Int, UserData]().withDefault(id => UserData(id))
	private[this] val dataForToon = mutable.Map[Int, Var[Toon]]()

	def mainForUser(user: Int): Rx[Toon] = dataForUser(user).main
}
