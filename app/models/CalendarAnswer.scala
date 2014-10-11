package models

import java.sql.Timestamp
import models.mysql._
import scala.slick.jdbc.JdbcBackend.SessionDef
import api.CalendarAnswerCreate
import gt.Socket
import api.CalendarAnswerUpdate

/**
 * One answer for one event for one user
 */
case class CalendarAnswer(user: Int, event: Int, date: Timestamp, answer: Int, note: Option[String], char: Option[Int]) {
	if (answer < 0 || answer > 2) {
		throw new Exception("Invalid answer value")
	}

	/**
	 * Convert this answer to a expanded answer tuple
	 */
	lazy val expand = buildExpand
	private def buildExpand: CalendarAnswerTuple = DB.withSession { implicit s =>
		var user_obj = Users.filter(_.id === user).first
		var char = Chars.filter(c => c.owner === user && c.active).list
		CalendarAnswerTuple(user_obj, Some(this), char)
	}
}

/**
 * Expanded version for event pages
 */
case class CalendarAnswerTuple(user: User, answer: Option[CalendarAnswer], chars: List[Char])

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

	def notifyUpdate(user: Int, event: Int)(implicit s: SessionDef): Unit = {
	}

	def notifyUpdate(answer: CalendarAnswer): Unit = {
		Socket !# CalendarAnswerUpdate(answer)
	}

	def notifyDelete(user: Int, event: Int): Unit = {
	}
}
