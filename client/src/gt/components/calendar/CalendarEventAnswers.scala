package gt.components.calendar

import _root_.data.UserGroups
import gt.Router
import gt.components.GtHandler
import gt.components.widget.form.GtButton
import gt.components.widget.{GtBox, GtContextMenu, GtTooltip}
import gt.services.{CalendarService, RosterService}
import models.calendar._
import models.{Toon, User}
import rx.{Const, Rx, Var}
import utils.DateTime
import utils.annotation.data
import utils.jsannotation.js
import xuen.Component

object CalendarEventAnswers extends Component[CalendarEventAnswers](
	selector = "calendar-event-answers",
	templateUrl = "/assets/imports/views/calendar-event.html",
	dependencies = Seq(GtBox, GtButton, CalendarUnitFrame, GtTooltip, GtContextMenu)
)

@js class CalendarEventAnswers extends GtHandler {
	val calendar = service(CalendarService)
	val roster = service(RosterService)

	val ss = calendar.slacks

	val event = property[Event]
	val slacks = event ~ { e =>
		calendar.slacks.ranges.containing(e.date.toCalendarKey).map { s =>
			(s.user, s.reason.getOrElse(""))
		}.toMap
	}

	def date: String = {
		val date = event.date
		s"${ date.day }/${ date.month }/${ date.year }"
	}

	def state: String = EventState.name(event.state)

	@data case class AnswerData(event: Event, answer: Answer, toon: Toon, user: User) {
		def hasNote = answer.note.isDefined
		def note = answer.note.get

		def hasSlack = slacks.contains(user.id)
		def slack = slacks(user.id)

		def datetime = answer.date.toISOString.replaceFirst("""^([0-9]+)\-([0-9]+)-([0-9]+)T([0-9]+):([0-9]+).*$""", "$3/$2/$1 – $4:$5")
		def pending = answer.answer == AnswerValue.Pending || answer.date == DateTime.zero

		def isOwner = event.owner == answer.user
		def isPromoted = user.promoted || answer.promote

		def hasGrant = app.user.promoted || event.owner == app.user.id
		def canBePromoted = hasGrant && !isPromoted
		def canBeDemoted = hasGrant && isPromoted && user.id != app.user.id && !user.promoted

		def star = {
			if (isOwner) 1
			else if (isPromoted) 2
			else 0
		}

		def gotoProfile(): Unit = Router.goto("/profile/" + user.id)
	}

	def withSyntheticAnswers(event: Event, answers: Set[Answer]): Set[Answer] = {
		lazy val slacks = CalendarService.slacks.ranges.containing(event.date.toCalendarKey).!.map(s => (s.user, s)).toMap
		def build(groups: Set[Int]) = {
			(groups.flatMap(g => RosterService.users.byGroup.get(g).map(_.id)) -- answers.map(_.user)).map { user =>
				val status = slacks.get(user) match {
					case Some(slack) => AnswerValue.Declined
					case None => AnswerValue.Pending
				}
				Answer(user, event.id, DateTime.zero, status, None, None, false)
			} ++ answers
		}

		event.visibility match {
			case EventVisibility.Roster => build(UserGroups.roster)
			case _ => answers
		}
	}

	val answers = event ~! { e =>
		if (e.owner > 0) calendar.answers.forEvent(e.id)
		else Const(Set.empty[Answer])
	} ~ { as =>
		withSyntheticAnswers(event, as).groupBy(a => a.answer) withDefaultValue Set.empty
	}

	val accepts = answers ~ (a => a(1).size)
	val declines = answers ~ (a => a(2).size)
	val pendings = answers ~ (a => a(0).size)

	val tab = Var(1)

	val tabAnswers = Rx {
		answers(tab).toSeq.map { a =>
			val toon = a.toon.map(roster.toon).getOrElse(roster.main(a.user)).!
			val user = roster.user(a.user).!
			AnswerData(event, a, toon, user)
		}.sorted(new Ordering[AnswerData] {
			def compare(x: AnswerData, y: AnswerData): Int = {
				if (x.toon.clss != y.toon.clss) x.toon.clss compare y.toon.clss
				else x.toon.name compare y.toon.name
			}
		})
	}

	def emptyTab = tabAnswers.size < 1
}
