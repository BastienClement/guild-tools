package actors

import scala.concurrent.duration._
import actors.Actors.Dispatcher
import actors.CalendarServiceShr.CalendarLock
import api.{CalendarLockAcquire, CalendarLockRelease}
import gt.Global.ExecutionContext
import models.mysql._
import models.{CalendarTab, CalendarTabs, _}
import utils.scheduler
import scala.concurrent.Future
import utils._

/**
 * Public interface
 */
trait CalendarService {
	def createTab(event: Int, title: String): Future[CalendarTab]
	def deleteTab(tab: Int): Unit
	def wipeTab(tab: Int): Unit
	def setTabHidden(tab: Int, locked: Boolean): Unit

	def tabStatus(tab: Int): Option[String]
	def lockTab(tab: Int, owner: String): Option[CalendarLock]
	def unlockTab(lock: CalendarLock): Unit

	def setSlot(template: CalendarSlot): Unit
	def setSlots(templates: List[CalendarSlot]): Unit
	def resetSlot(tab: Int, slot: Int): Unit

	def optimizeEvent(event: Int): Unit
}

/**
 * Common shared elements
 */
object CalendarServiceShr {
	/**
	 * Time before lock expiration
	 */
	private val LockExpireDuration = 15.seconds

	/**
	 * Calendar lock subclass
	 */
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
		 * Release this lock
		 */
		def release(): Unit = {
			if (valid) {
				valid = false
				Actors.CalendarService.unlockTab(this)
			}
		}
	}
}

/**
 * Actor implementation
 */
class CalendarServiceImpl extends CalendarService {
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

	def createTab(event: Int, title: String): Future[CalendarTab] = {
		DB.withSession { implicit s =>
			val max_order = CalendarTabs.filter(_.event === event).map(_.order).max.run.get
			val template = CalendarTab(0, event, title, None, max_order + 1, false, false)
			val id = (CalendarTabs returning CalendarTabs.map(_.id)).insert(template)
			val tab = template.copy(id = id)
			CalendarTabs.notifyCreate(tab)
			tab
		}
	}

	def deleteTab(tab: Int): Unit = {
		DB.withSession { implicit s =>
			if (CalendarTabs.filter(t => t.id === tab && !t.undeletable).delete > 0) {
				CalendarTabs.notifyDelete(tab)
			} else {
				wipeTab(tab)
			}
		}
	}

	def wipeTab(tab: Int): Unit = {
		DB.withSession { implicit s =>
			if (CalendarSlots.filter(_.tab === tab).delete > 0) {
				CalendarTabs.notifyWipe(tab)
			}
		}
	}

	def setTabHidden(tab: Int, locked: Boolean): Unit = {
		DB.withSession { implicit s =>
			val tab_query = CalendarTabs.filter(_.id === tab)
			if (tab_query.map(_.locked).update(locked) > 0) {
				CalendarTabs.notifyUpdate(tab_query.first)
			}
		}
	}

	def lockTab(tab: Int, owner: String): Option[CalendarLock] = {
		if (locks.contains(tab)) {
			None
		} else {
			val lock = new CalendarLock(tab, owner)
			locks += (tab -> lock)
			Dispatcher !# CalendarLockAcquire(tab, owner)
			Some(lock)
		}
	}

	def tabStatus(tab: Int): Option[String] = locks.get(tab).map(_.owner)

	def unlockTab(lock: CalendarLock): Unit = {
		for (l <- locks.get(lock.tab)) {
			if (lock == l) {
				locks -= lock.tab
				Dispatcher !# CalendarLockRelease(lock.tab)
			}
		}
	}

	def setSlot(template: CalendarSlot): Unit = {
		DB.withSession { implicit s =>
			CalendarSlots.filter(s => s.tab === template.tab && s.owner === template.owner).delete
			CalendarSlots.insertOrUpdate(template)
			CalendarSlots.notifyUpdate(template)
		}
	}

	def setSlots(templates: List[CalendarSlot]): Unit = {
		DB.withSession { implicit s =>
			CalendarSlots ++= templates
			for (t <- templates) CalendarSlots.notifyUpdate(t)
		}
	}

	def resetSlot(tab: Int, slot: Int): Unit = {
		DB.withSession { implicit s =>
			CalendarSlots.filter(s => s.tab === tab && s.slot === slot).delete
			CalendarSlots.notifyDelete(tab, slot)
		}
	}

	def optimizeEvent(event: Int): Unit = {
		DB.withSession { implicit s =>
			val tabs = CalendarTabs.filter(_.event === event).list

			if (tabs.size > 1) {
				// Load every tabs contents
				val slots = CalendarSlots.filter(_.tab inSet tabs.map(_.id).toSet).list.groupBy(_.tab)

				// Fetch the default tab
				val locked_tab = tabs.find(_.undeletable).get

				// Do not delete a non-empty default tab
				if (slots.get(locked_tab.id).isEmpty) {
					// Fetch a replacement default tab and make it undeletable
					val replacement = tabs.find(!_.undeletable).get.copy(undeletable = true)
					CalendarTabs.filter(_.id === replacement.id).update(replacement)
					CalendarTabs.notifyUpdate(replacement)

					// Remove the old one
					CalendarTabs.filter(_.id === locked_tab.id).delete
					CalendarTabs.notifyDelete(locked_tab.id)
				}
			}
		}
	}
}
