package actors

import scala.concurrent.duration._
import actors.CalendarLockManager._
import api.{CalendarLockAcquire, CalendarLockRelease}
import gt.Global.ExecutionContext
import gt.Socket
import utils.scheduler

trait CalendarLockManagerInterface {
	def acquire(tab: Int, owner: String): Option[CalendarLock]
	def status(tab: Int): Option[String]
	def release(lock: CalendarLock): Unit
}

object CalendarLockManager {
	private val LockExpireDuration = 15.seconds
	class CalendarLock(val tab: Int, val owner: String) {
		/**
		 * Lock is valid until released
		 */
		var valid = true

		/**
		 * Keep last refresh time
		 */
		var deadline: Deadline = LockExpireDuration.fromNow

		/**
		 * Refresh the lock to prevent garbage collection
		 */
		def refresh(): Unit = { deadline = LockExpireDuration.fromNow }

		/**
		 * Release this log
		 */
		def release(): Unit = {
			if (valid) {
				valid = false
				Actors.CalendarLockManager.release(this)
			}
		}
	}
}

class CalendarLockManager extends CalendarLockManagerInterface {
	/**
	 * Every locks currently in use
	 */
	private var locks: Map[Int, CalendarLock] = Map()

	/**
	 * Locks garbage collector
	 */
	scheduler.schedule(5.second, 5.second) {
		for (lock <- locks.values if lock.deadline.isOverdue()) lock.release()
	}

	def acquire(tab: Int, owner: String): Option[CalendarLock] = {
		if (locks.contains(tab)) {
			None
		} else {
			val lock = new CalendarLock(tab, owner)
			locks += (tab -> lock)
			Socket !# CalendarLockAcquire(tab, owner)
			Some(lock)
		}
	}

	def status(tab: Int): Option[String] = locks.get(tab).map(_.owner)

	def release(lock: CalendarLock): Unit = {
		for (l <- locks.get(lock.tab)) {
			if (lock == l) {
				locks -= lock.tab
				Socket !# CalendarLockRelease(lock.tab)
			}
		}
	}
}
