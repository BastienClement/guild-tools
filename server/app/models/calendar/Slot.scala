package models.calendar

import models.Toons
import models.mysql._

case class Slot(tab: Int, slot: Int, owner: Int, name: String, `class`: Int, role: String) {
	if (!Toons.validateRole(role)) {
		throw new Exception("Invalid role value")
	}

	if (slot < 1 || slot > 30) {
		throw new Exception("Invalid slot ID")
	}
}

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
