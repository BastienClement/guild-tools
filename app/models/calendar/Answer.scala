package models.calendar

import java.sql.Timestamp
import models.mysql._
import models._

case class Answer(user: Int, event: Int, date: Timestamp, answer: Int, note: Option[String], char: Option[Int], promote: Boolean) {
	if (answer < 0 || answer > 2) {
		throw new Exception("Invalid answer value")
	}

	lazy val fullEvent = Events.filter(_.id === event).head
}

class Answers(tag: Tag) extends Table[Answer](tag, "gt_answers") {
	def user = column[Int]("user", O.PrimaryKey)
	def event = column[Int]("event", O.PrimaryKey)
	def date = column[Timestamp]("date")
	def answer = column[Int]("answer")
	def note = column[Option[String]]("note")
	def char = column[Option[Int]]("char")
	def promote = column[Boolean]("promote")

	def * = (user, event, date, answer, note, char, promote) <> (Answer.tupled, Answer.unapply)
}

object Answers extends TableQuery(new Answers(_)) {
	val Pending = 0
	val Accepted = 1
	val Declined = 2
}
