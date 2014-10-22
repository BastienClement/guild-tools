package models

import models.mysql._
import scala.slick.jdbc.JdbcBackend.SessionDef
import gt.Socket
import api._

case class CalendarTab(id: Int, event: Int, title: String, note: Option[String], order: Int, locked: Boolean, undeletable: Boolean) {
	lazy val expandEvent = DB.withSession { implicit s =>
		CalendarEvents.filter(_.id === event).first
	}
}

/**
 * Answers database
 */
class CalendarTabs(tag: Tag) extends Table[CalendarTab](tag, "gt_events_tabs") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def event = column[Int]("event")
	def title = column[String]("title")
	def note = column[Option[String]]("note")
	def order = column[Int]("order")
	def locked = column[Boolean]("locked")
	def undeletable = column[Boolean]("undeletable")

	def * = (id, event, title, note, order, locked, undeletable) <> (CalendarTab.tupled, CalendarTab.unapply)
}

/**
 * Helpers
 */
object CalendarTabs extends TableQuery(new CalendarTabs(_)) {
	def notifyCreate(tab: CalendarTab): Unit = {
		Socket !# CalendarTabCreate(tab)
	}

	def notifyUpdate(tab: CalendarTab): Unit = {
		Socket !# CalendarTabUpdate(tab)
	}

	def notifyDelete(id: Int): Unit = {
		Socket !# CalendarTabDelete(id)
	}

	def notifyWipe(id: Int): Unit = {
		Socket !# CalendarTabWipe(id)
	}
}
