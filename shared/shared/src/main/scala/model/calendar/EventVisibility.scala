package model.calendar

/**
  * Visibility of a calendar event
  */
object EventVisibility {
	final val Roster = 1
	final val Public = 2
	final val Restricted = 3
	final val Announce = 4
	final val Guild = 5

	def isValid(v: Int) = v > 0 && v < 6
}
