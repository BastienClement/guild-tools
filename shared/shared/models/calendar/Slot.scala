package models.calendar

import boopickle.DefaultBasic._
import utils.annotation.data

@data case class Slot(tab: Int, slot: Int, owner: Int, name: String, `class`: Int, role: String) {
	require(slot >= 1 && slot <= 30, "Invalid slot ID")
}

object Slot {
	implicit val SlotPickler = PicklerGenerator.generatePickler[Slot]
}
