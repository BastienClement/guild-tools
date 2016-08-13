package model.calendar

/**
  * State of a calendar Event
  */
object EventState {
	val Open = 0
	val Closed = 1
	val Canceled = 2

	def isValid(s: Int) = s >= 0 && s <= 2
}
