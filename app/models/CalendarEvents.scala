package models

import java.sql.Timestamp
import models.mysql._
import scala.slick.jdbc.JdbcBackend.SessionDef
import api.CalendarEventCreate
import gt.Socket
import api.CalendarEventDelete

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
}

case class CalendarEvent(id: Int, title: String, desc: String, owner: Int, date: Timestamp, time: Int, `type`: Int, state: Int) {
	val visibility = `type`
}

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

	def notifyUpdate(id: Int)(implicit s: SessionDef): Unit = {
	}

	def notifyDelete(id: Int): Unit = {
		Socket !# CalendarEventDelete(id)
	}
}
