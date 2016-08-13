package model.calendar

/**
  * Created by galedric on 11.08.2016.
  */
case class Slot(tab: Int, slot: Int, owner: Int, name: String, `class`: Int, role: String) {
	require(slot >= 1 && slot <= 30, "Invalid slot ID")
}
