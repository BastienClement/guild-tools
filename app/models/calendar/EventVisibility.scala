package models.calendar

/**
  * Visibility of a calendar event
  */
object EventVisibility {
	val Roster = 1
	val Public = 2
	val Restricted = 3
	val Announce = 4
	val Guild = 5
	def isValid(v: Int) = v > 0 && v < 6
}
