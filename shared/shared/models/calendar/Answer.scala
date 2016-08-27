package models.calendar

import boopickle.DefaultBasic._
import utils.DateTime
import utils.annotation.data

@data case class Answer(user: Int, event: Int, date: DateTime, answer: Int,
                        note: Option[String], toon: Option[Int], promote: Boolean) {
	if (answer < 0 || answer > 2) throw new Exception("Invalid answer value")
}

object Answer {
	implicit val AnswerPickler = PicklerGenerator.generatePickler[Answer]

	final val Pending = 0
	final val Accepted = 1
	final val Declined = 2
}
