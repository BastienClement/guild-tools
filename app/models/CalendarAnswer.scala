package models

import java.sql.Timestamp

import models.mysql._

import scala.slick.jdbc.JdbcBackend.SessionDef

case class CalendarAnswer(user: Int, event: Int, date: Timestamp, answer: Int, note: String, char: Int)

class CalendarAnswers(tag: Tag) extends Table[CalendarAnswer](tag, "gt_answers") {
	def user = column[Int]("user")
	def event = column[Int]("event")
	def date = column[Timestamp]("date")
	def answer = column[Int]("answer")
	def note = column[String]("note")
	def char = column[Int]("char")

	def * = (user, event, date, answer, note, char) <> (CalendarAnswer.tupled, CalendarAnswer.unapply)
}

object CalendarAnswers extends TableQuery(new CalendarAnswers(_)) {
	def notifyCreate(answer: CalendarAnswer): Unit = {
	}

	def notifyUpdate(user: Int, event: Int)(implicit s: SessionDef): Unit = {
	}

	def notifyDelete(user: Int, event: Int): Unit = {
	}
}
