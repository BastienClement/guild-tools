package models

import java.sql.Timestamp
import models._
import models.mysql._
import scala.slick.jdbc.JdbcBackend.SessionDef
import api.CalendarEventCreate
import gt.Socket
import api.CalendarEventDelete
import api.CalendarEventUpdate
import api.CalendarHelper

object CalendarVisibility {
	val Guild = 1
	val Public = 2
	val Private = 3
	val Announce = 4
	def isValid(v: Int) = (v > 0 && v < 5)
}

object CalendarEventState {
	val Open = 0
	val Closed = 1
	val Canceled = 2
	def isValid(s: Int) = (s >= 0 && s <= 2)
}

case class CalendarEvent(id: Int, title: String, desc: String, owner: Int, date: Timestamp, time: Int, `type`: Int, state: Int) {
	val visibility = `type`

	/**
	 * Expand this event to include tabs and slots data
	 */
	lazy val expand = {
		DB.withSession { implicit s =>
			// Fetch tabs
			val tabs = CalendarTabs.filter(_.event === this.id).list

			// Fetch slots
			val slots = CalendarSlots.filter(_.tab inSet tabs.map(_.id).toSet).list.groupBy(_.tab.toString).mapValues {
				_.map(s => (s.slot.toString, s)).toMap
			}

			// Build full object
			CalendarEventFull(this, tabs, slots)
		}
	}

	/**
	 * Create an full but concealed version of this event
	 */
	lazy val conceal = {
		val tabs = List[CalendarTab](CalendarTab(0, this.id, "Default", None, 0, true))
		val slots = Map[String, Map[String, CalendarSlot]]()
		CalendarEventFull(this, tabs, slots)
	}
}

case class CalendarEventFull(event: CalendarEvent, tabs: List[CalendarTab], slots: Map[String, Map[String, CalendarSlot]])

class CalendarEvents(tag: Tag) extends Table[CalendarEvent](tag, "gt_events") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def title = column[String]("title")
	def desc = column[String]("desc")
	def owner = column[Int]("owner")
	def date = column[Timestamp]("date")
	def time = column[Int]("time")
	def visibility = column[Int]("type")
	def state = column[Int]("state")

	def * = (id, title, desc, owner, date, time, visibility, state) <> (CalendarEvent.tupled, CalendarEvent.unapply)
}

object CalendarEvents extends TableQuery(new CalendarEvents(_)) {
	def notifyCreate(event: CalendarEvent): Unit = {
		Socket !# CalendarEventCreate(event)
	}

	def notifyUpdate(event: CalendarEvent): Unit = {
		Socket !# CalendarEventUpdate(event)
	}

	def notifyDelete(id: Int): Unit = {
		Socket !# CalendarEventDelete(id)
	}
}
