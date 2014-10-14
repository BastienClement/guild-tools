package models

import models.mysql._
import scala.slick.jdbc.JdbcBackend.SessionDef
import gt.Socket
import api.CalendarSlotUpdate
import api.CalendarSlotDelete

case class CalendarSlot(tab: Int, slot: Int, owner: Int, name: String, `class`: Int, role: String) {
	if (!Chars.validateRole(role)) {
		throw new Exception("Invalid role value")
	}
}

/**
 * Answers database
 */
class CalendarSlots(tag: Tag) extends Table[CalendarSlot](tag, "gt_events_slots") {
	def tab = column[Int]("tab", O.PrimaryKey)
	def slot = column[Int]("slot")
	def owner = column[Int]("owner")
	def name = column[String]("name")
	def clazz = column[Int]("class")
	def role = column[String]("role")

	def * = (tab, slot, owner, name, clazz, role) <> (CalendarSlot.tupled, CalendarSlot.unapply)
}

/**
 * Helpers
 */
object CalendarSlots extends TableQuery(new CalendarSlots(_)) {
	def notifyUpdate(slot: CalendarSlot): Unit = {
		Socket !# CalendarSlotUpdate(slot)
	}

	def notifyDelete(tab: Int, slot: Int): Unit = {
		Socket !# CalendarSlotDelete(tab, slot)
	}
}
