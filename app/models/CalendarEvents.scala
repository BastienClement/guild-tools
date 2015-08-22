package models

import java.sql.Timestamp

import gt.Global.ExecutionContext
import models.mysql._

object CalendarVisibility {
	val Roster = 1
	val Public = 2
	val Restricted = 3
	val Announce = 4
	val Guild = 5
	def isValid(v: Int) = v > 0 && v < 6
}

object CalendarEventState {
	val Open = 0
	val Closed = 1
	val Canceled = 2
	def isValid(s: Int) = s >= 0 && s <= 2
}

case class CalendarEvent(id: Int, title: String, desc: String, owner: Int, date: Timestamp, time: Int, `type`: Int, state: Int) {
	val visibility = `type`

	// Check visibility and state
	if (!CalendarVisibility.isValid(visibility)) throw new Exception("Invalid event visibility")
	if (!CalendarEventState.isValid(state)) throw new Exception("Invalid event state")

	val isRestricted = visibility == CalendarVisibility.Restricted
	val isAnnounce = visibility == CalendarVisibility.Announce

	/**
	 * Expand this event to include tabs and slots data
	 */
	lazy val expand =
		for {
			tabs <- CalendarTabs.filter(_.event === this.id).run
			slots <- CalendarSlots.filter(_.tab inSet tabs.map(_.id).toSet).run
		} yield {
			val slots_map = slots.groupBy(_.tab.toString).mapValues {
				_.map(s => (s.slot.toString, s)).toMap
			}
			CalendarEventFull(this, tabs.toList, slots_map)
		}

	/**
	 * Create a partially visible expanded version of this event
	 */
	lazy val partial = for (expanded <- this.expand) yield {
		// Remove note from hidden tab
		var visibles_tabs = Set[String]()
		val tabs = expanded.tabs map { tab =>
			if (tab.locked) {
				tab.copy(note = None)
			} else {
				visibles_tabs += tab.id.toString
				tab
			}
		}

		// Rebuild slots for visible tabs
		val slots = expanded.slots.filter { case (id, slots) => visibles_tabs.contains(id) }

		CalendarEventFull(this, tabs, slots)
	}
}

case class CalendarEventFull(event: CalendarEvent, tabs: List[CalendarTab], slots: Map[String, Map[String, CalendarSlot]])

class CalendarEvents(tag: Tag) extends Table[CalendarEvent](tag, "gt_events_visible") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def title = column[String]("title")
	def desc = column[String]("desc")
	def owner = column[Int]("owner")
	def date = column[Timestamp]("date")
	def time = column[Int]("time")
	def visibility = column[Int]("type")
	def state = column[Int]("state")
	def garbage = column[Boolean]("garbage")

	def * = (id, title, desc, owner, date, time, visibility, state) <>(CalendarEvent.tupled, CalendarEvent.unapply)
}

object CalendarEvents extends TableQuery(new CalendarEvents(_)) {

}
