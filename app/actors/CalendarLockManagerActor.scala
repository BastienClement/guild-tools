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

trait CalendarLockManagerInterface {
	def acquire(tab: Int, owner: String): Option[CalendarLock]
	def status(tab: Int): Option[String]
	def release(lock: CalendarLock): Unit
}

object CalendarLockManagerActor {
	val CalendarLockManager: CalendarLockManagerInterface = TypedActor(Akka.system).typedActorOf(TypedProps[CalendarLockManagerActor]())

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
				CalendarLockManager.release(this)
			}
		}
	}
}

class CalendarLockManagerActor extends CalendarLockManagerInterface {
	/**
	 * Every locks currently in use
	 */
	private var locks: Map[Int, CalendarLock] = Map()

	/**
	 * Locks garbage collector
	 */
	scheduler.schedule(5.second, 5.second) {
		val now = new Date().getTime
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
