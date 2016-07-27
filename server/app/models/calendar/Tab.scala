package models.calendar

import models._
import models.mysql._

case class Tab(id: Int, event: Int, title: String, note: Option[String], order: Int, locked: Boolean, undeletable: Boolean) {
	lazy val expandEvent = Events.filter(_.id === event).head
}

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

object Tabs extends TableQuery(new Tabs(_))
