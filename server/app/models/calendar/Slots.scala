package models.calendar

import models.calendar.Slot
import models.mysql._

class Slots(tag: Tag) extends Table[Slot](tag, "gt_events_slots") {
	def tab = column[Int]("tab", O.PrimaryKey)
	def slot = column[Int]("slot")
	def owner = column[Int]("owner")
	def name = column[String]("name")
	def clazz = column[Int]("class")
	def role = column[String]("role")

	def * = (tab, slot, owner, name, clazz, role) <> (Slot.tupled, Slot.unapply)
}

object Slots extends TableQuery(new Slots(_))
