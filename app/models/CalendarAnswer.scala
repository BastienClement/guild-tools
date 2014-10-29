package models

import java.sql.Timestamp

import api.{CalendarAnswerCreate, CalendarAnswerUpdate}
import gt.Socket
import models.mysql._

/**
 * One answer for one event for one user
 */
case class CalendarAnswer(user: Int, event: Int, date: Timestamp, answer: Int, note: Option[String], char: Option[Int]) {
	if (answer < 0 || answer > 2) {
		throw new Exception("Invalid answer value")
	}
}

/**
 * Answers database
 */
class CalendarAnswers(tag: Tag) extends Table[CalendarAnswer](tag, "gt_answers") {
	def user = column[Int]("user", O.PrimaryKey)
	def event = column[Int]("event", O.PrimaryKey)
	def date = column[Timestamp]("date")
	def answer = column[Int]("answer")
	def note = column[Option[String]]("note")
	def char = column[Option[Int]]("char")

	def * = (user, event, date, answer, note, char) <> (CalendarAnswer.tupled, CalendarAnswer.unapply)
}

/**
 * Helpers
 */
object CalendarAnswers extends TableQuery(new CalendarAnswers(_)) {
	def notifyCreate(answer: CalendarAnswer): Unit = {
		Socket !# CalendarAnswerCreate(answer)
	}

	def notifyUpdate(answer: CalendarAnswer): Unit = {
		Socket !# CalendarAnswerUpdate(answer)
	}

	def notifyDelete(user: Int, event: Int): Unit = {
	}
}
