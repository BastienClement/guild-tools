package actors

import java.util.Date
import scala.concurrent.duration._
import actors.CalendarLockManagerActor._
import akka.actor._
import api.{CalendarLockAcquire, CalendarLockRelease}
import gt.Global.ExecutionContext
import gt.Socket
import play.api.Play.current
import play.api.libs.concurrent.Akka
import utils.scheduler

object CalendarLockManagerActor {
	val CalendarLockManager = Akka.system.actorOf(Props[CalendarLockManagerActor], name = "CalendarLockManager")

	case class LockAcquire(tab: Int, owner: String)
	case class LockStatus(tab: Int)
	case class LockRelease(lock: CalendarLock)

	class CalendarLock(val tab: Int, val owner: String) {
		/**
		 * Lock is valid until released
		 */
		var valid = true

		/**
		 * Keep last refresh time
		 */
		var lastRefresh: Long = 0

		/**
		 * Refresh the lock to prevent garbage collection
		 */
		def refresh(): Unit = { lastRefresh = new Date().getTime }
		refresh()

		/**
		 * Release this log
		 */
		def release(): Unit = {
			if (valid) {
				valid = false
				CalendarLockManager ! LockRelease(this)
			}
		}
	}
}

class CalendarLockManagerActor extends Actor {
	/**
	 * Every locks currently in use
	 */
	private var locks: Map[Int, CalendarLock] = Map()

	/**
	 * Locks garbage collector
	 */
	scheduler.schedule(5.second, 5.second) {
		val now = new Date().getTime
		locks.values.filter(now - _.lastRefresh > 15000) foreach (_.release())
	}

	def receive = {
		case LockAcquire(tab, owner) => {
			if (locks.contains(tab)) {
				sender ! None
			} else {
				val lock = new CalendarLock(tab, owner)
				locks += (tab -> lock)
				Socket !# CalendarLockAcquire(tab, owner)
				sender ! Some(lock)
			}
		}

		case LockStatus(tab) => {
			sender ! locks.get(tab).map(_.owner)
		}

		case LockRelease(lock) => {
			locks.get(lock.tab) foreach { l =>
				if (lock == l) {
					locks -= lock.tab
					Socket !# CalendarLockRelease(lock.tab)
				}
			}
		}
	}
}
