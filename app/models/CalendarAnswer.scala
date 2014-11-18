package models

import java.sql.Timestamp
import actors.Actors.EventDispatcher
import api.{CalendarAnswerCreate, CalendarAnswerDelete, CalendarAnswerUpdate}
import models.mysql._

/**
 * One answer for one event for one user
 */
case class CalendarAnswer(user: Int, event: Int, date: Timestamp, answer: Int, note: Option[String], char: Option[Int], promote: Boolean) {
	if (answer < 0 || answer > 2) {
		throw new Exception("Invalid answer value")
	}

	lazy val fullEvent: CalendarEvent = DB.withSession { implicit s =>
		CalendarEvents.filter(_.id === event).first
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
	def promote = column[Boolean]("promote")

	def * = (user, event, date, answer, note, char, promote) <> (CalendarAnswer.tupled, CalendarAnswer.unapply)
}

/**
 * Helpers
 */
object CalendarAnswers extends TableQuery(new CalendarAnswers(_)) {
	def notifyCreate(answer: CalendarAnswer): Unit = {
		EventDispatcher !# CalendarAnswerCreate(answer)
	}

	def notifyUpdate(answer: CalendarAnswer): Unit = {
		EventDispatcher !# CalendarAnswerUpdate(answer)
	}

	def notifyDelete(user: Int, event: Int): Unit = {
		EventDispatcher !# CalendarAnswerDelete(user, event)
	}
}
