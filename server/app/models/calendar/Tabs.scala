package models.calendar

import models.calendar.Tab
import models._
import models.mysql._

class Tabs(tag: Tag) extends Table[Tab](tag, "gt_events_tabs") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def event = column[Int]("event")
	def title = column[String]("title")
	def note = column[Option[String]]("note")
	def order = column[Int]("order")
	def locked = column[Boolean]("locked")
	def undeletable = column[Boolean]("undeletable")

	def * = (id, event, title, note, order, locked, undeletable) <> (Tab.tupled, Tab.unapply)
}

object Tabs extends TableQuery(new Tabs(_)) {
	def expandEvent(tab: Tab) = Events.filter(_.id === tab.event).head
}
