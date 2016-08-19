package model.calendar

/**
  * State of a calendar Event
  */
object EventState {
	final val Open = 0
	final val Closed = 1
	final val Canceled = 2

	def isValid(s: Int) = 0 <= s && s <= 2
}
