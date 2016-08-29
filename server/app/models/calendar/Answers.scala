package models.calendar

import models.{Toons, User}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import slick.lifted
import utils.SlickAPI._
import utils.{DateTime, PubSub}

class Answers(tag: Tag) extends Table[Answer](tag, "gt_answers") {
	def user = column[Int]("user", O.PrimaryKey)
	def event = column[Int]("event", O.PrimaryKey)
	def date = column[DateTime]("date")
	def answer = column[Int]("answer")
	def note = column[Option[String]]("note")
	def char = column[Option[Int]]("char")
	def promote = column[Boolean]("promote")

	def * = (user, event, date, answer, note, char, promote) <> ((Answer.apply _).tupled, Answer.unapply)
}

object Answers extends TableQuery(new Answers(_)) with PubSub[User] {
	case class Updated(answer: Answer)

	def findForEvent(event: lifted.Rep[Int]) = {
		Answers.filter(_.event === event)
	}

	def findForEventAndUser(event: lifted.Rep[Int], user: lifted.Rep[Int]) = {
		findForEvent(event).filter(_.user === user)
	}

	def withOwnAnswer(events: Query[Events, Event, Seq], user: User) = {
		events.joinLeft(Answers).on { case (e, a) => e.id === a.event && a.user === user.id}
	}

	def fullEvent(answer: Answer) = Events.filter(_.id === answer.event).head

	def changeAnswer(user: Int, event: Int, answer: Int, toon: Option[Int], note: Option[String]): Unit = {
		for {
			ev <- Events.findById(event).head
			_ = if (ev.state != EventState.Open) throw new Exception("Cannot answer to a closed or canceled event")
			old <- Answers.findForEventAndUser(event, user).headOption
			toonData <- toon.map(Toons.findById(_).headOption).getOrElse(Future.successful(None))
			_ = if (!toonData.forall(_.owner == user)) throw new Exception("Illegal toon given")
		} {
			old match {
				case Some(o) if o.answer != answer || o.toon != toon || o.note != note =>
					val updated = o.copy(date = DateTime.now, answer = answer, toon = toon, note = note)
					for (n <- Answers.findForEventAndUser(event, user).update(updated).run if n > 0) publishUpdate(updated)
				case None =>
					val inserted = Answer(user, event, DateTime.now, answer, note, toon, false)
					for (n <- (Answers += inserted).run if n > 0) publishUpdate(inserted)
				case _ => // Ignore
			}
		}
	}

	def publishUpdate(answer: Answer): Unit = {
		for {
			event <- Events.findById(answer.event).head
			answers <- for (as <- Answers.findForEvent(event.id).run) yield as.map(a => (a.user, a)).toMap
		} {
			publish(Updated(answer), u => Events.canAccess(u, event, answers.get(u.id)))
		}
	}
}
