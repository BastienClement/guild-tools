package model.calendar

import util.DateTime
import util.annotation.data

@data case class Answer(user: Int, event: Int, date: DateTime, answer: Int,
                        note: Option[String], toon: Option[Int], promote: Boolean) {
	if (answer < 0 || answer > 2) throw new Exception("Invalid answer value")
}
