package models

import java.sql.Timestamp
import models.mysql._
import scala.slick.jdbc.JdbcBackend.SessionDef
import api.CalendarAnswerCreate
import gt.Socket
import api.CalendarAnswerUpdate

case class CalendarAnswer(user: Int, event: Int, date: Timestamp, answer: Int, note: Option[String], char: Option[Int]) {
	if (answer < 0 || answer > 2) {
		throw new Exception("Invalid answer value")
	}
}

class CalendarAnswers(tag: Tag) extends Table[CalendarAnswer](tag, "gt_answers") {
	def user = column[Int]("user", O.PrimaryKey)
	def event = column[Int]("event", O.PrimaryKey)
	def date = column[Timestamp]("date")
	def answer = column[Int]("answer")
	def note = column[Option[String]]("note")
	def char = column[Option[Int]]("char")

	def * = (user, event, date, answer, note, char) <> (CalendarAnswer.tupled, CalendarAnswer.unapply)
}

object CalendarAnswers extends TableQuery(new CalendarAnswers(_)) {
	def notifyCreate(answer: CalendarAnswer): Unit = {
		Socket !# CalendarAnswerCreate(answer)
	}

	def notifyUpdate(user: Int, event: Int)(implicit s: SessionDef): Unit = {
	}

	def notifyUpdate(answer: CalendarAnswer): Unit = {
		Socket !# CalendarAnswerUpdate(answer)
	}

	def notifyDelete(user: Int, event: Int): Unit = {
	}
}
