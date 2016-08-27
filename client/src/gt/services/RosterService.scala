package gt.services

import api.Roster.UserData
import boopickle.DefaultBasic._
import gt.services.base.{Cache, Delegate, Service}
import models.{Toon, User}
import rx.Rx
import scala.concurrent.Future

object RosterService extends Service with Delegate {
	/** The roster channel */
	private[this] val channel = registerChannel("roster", lzy = false)

	private[RosterService] def loadData(data: UserData): Unit = Rx.atomically {
		users.update(data.user)
		data.toons.foreach(toons.update)
	}

	def loadRoster(): Future[Unit] = {
		channel.request("load-roster") { datas: Iterable[UserData] =>
			Rx.atomically { datas.foreach(loadData) }
		}
	}

	object users extends Cache((u: User) => u.id) {
		val byGroup = new SimpleIndex(u => u.group)

		override def default(key: Int): User = {
			if (key > 0) channel.request("load-user", key)(loadData _)
			User(0, s"User#$key", 0)
		}
	}

	object toons extends Cache((t: Toon) => t.id) {
		val byOwner = new SimpleIndex({ case t: Toon if t.id > 0 => t.owner }: PartialFunction[Toon, Int])
		val mainForUser = new SimpleIndex({ case t: Toon if t.main && t.id > 0 => t.owner }: PartialFunction[Toon, Int])

		override def default(key: Int): Toon = {
			if (key > 0) {
				channel.request("load-user-toon", key)(loadData _)
				Toon(-1, s"Toon#$key", "Unknown", 0, false, true, 0, 0, 0, 0, 0, null, 0, 0, true, 0)
			} else if (key < 0) {
				val user = -key
				users.get(user)
				Toon(-1, s"User#$user", "Unknown", user, true, true, 0, 0, 0, 0, 0, null, 0, 0, true, 0)
			} else {
				Toon(-1, s"Toon#$key", "Unknown", 0, false, true, 0, 0, 0, 0, 0, null, 0, 0, true, 0)
			}
		}
	}

	def user(user: Int): Rx[User] = users.get(user)
	def toons(user: Int): Rx[Seq[Toon]] = toons.byOwner.get(user) ~ (_.toSeq.sorted)
	def toon(toon: Int): Rx[Toon] = toons.get(toon)
	def main(user: Int): Rx[Toon] = toons.mainForUser.get(user) ~ (_.headOption.getOrElse(toons.get(-user).!))

	def changeToonSpec(tid: Int, spec: Int): Unit = channel.send("change-toon-spec", (tid, spec))
	def promoteToon(tid: Int): Unit = channel.send("promote-toon", tid)
	def enableToon(tid: Int): Unit = channel.send("enable-toon", tid)
	def disableToon(tid: Int): Unit = channel.send("disable-toon", tid)
	def removeToon(tid: Int): Unit = channel.send("remove-toon", tid)

	def updateToon(tid: Int): Future[Unit] = channel.request("update-toon", tid).as[Unit]

	message("toon-updated")(toons.update _)
	message("toon-deleted")(toons.remove _)
}
