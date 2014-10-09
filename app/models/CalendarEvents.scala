package models

import java.sql.Timestamp

import models.mysql._

import scala.slick.jdbc.JdbcBackend.SessionDef

case class CalendarEvent(id: Int, title: String, desc: String, owner: Int, date: Timestamp, time: Int, `type`: Int, state: Int)

class CalendarEvents(tag: Tag) extends Table[CalendarEvent](tag, "gt_events") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def title = column[String]("title")
	def desc = column[String]("desc")
	def owner = column[Int]("owner")
	def date = column[Timestamp]("date")
	def time = column[Int]("time")
	def etype = column[Int]("type")
	def state = column[Int]("state")

	def * = (id, title, desc, owner, date, time, etype, state) <> (CalendarEvent.tupled, CalendarEvent.unapply)
}

object CalendarEvents extends TableQuery(new CalendarEvents(_)) {
	def notifyCreate(event: CalendarEvent): Unit = {
	}

	def notifyUpdate(id: Int)(implicit s: SessionDef): Unit = {
	}

	def notifyDelete(id: Int): Unit = {
	}
}
