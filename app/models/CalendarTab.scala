package models

import models.mysql._
import scala.slick.jdbc.JdbcBackend.SessionDef
import gt.Socket

case class CalendarTab(id: Int, event: Int, title: String, note: Option[String], order: Int)

/**
 * Answers database
 */
class CalendarTabs(tag: Tag) extends Table[CalendarTab](tag, "gt_events_tabs") {
	def id = column[Int]("id", O.PrimaryKey)
	def event = column[Int]("event")
	def title = column[String]("title")
	def note = column[Option[String]]("note")
	def order = column[Int]("order")

	def * = (id, event, title, note, order) <> (CalendarTab.tupled, CalendarTab.unapply)
}

/**
 * Helpers
 */
object CalendarTabs extends TableQuery(new CalendarTabs(_)) {
	def notifyCreate(tab: CalendarTab): Unit = {
	}

	def notifyUpdate(id: Int)(implicit s: SessionDef): Unit = {
	}

	def notifyUpdate(tab: CalendarTab): Unit = {
	}

	def notifyDelete(id: Int): Unit = {
	}
}
