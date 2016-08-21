package gt.component.calendar

import gt.component.GtHandler
import gt.component.calendar.CalendarEventAnswers.AnswerData
import gt.component.widget.form.GtButton
import gt.component.widget.{GtBox, GtContextMenu, GtTooltip}
import gt.service.{CalendarService, RosterService}
import model.calendar.{Answer, AnswerValue, Event, EventState}
import model.{Toon, User}
import rx.{Const, Rx, Var}
import util.annotation.data
import util.jsannotation.js
import xuen.Component

object CalendarEventAnswers extends Component[CalendarEventAnswers](
	selector = "calendar-event-answers",
	templateUrl = "/assets/imports/views/calendar-event.html",
	dependencies = Seq(GtBox, GtButton, CalendarUnitFrame, GtTooltip, GtContextMenu)
) {
	@data case class AnswerData(event: Event, answer: Answer, toon: Toon, user: User) {
		def hasNote = answer.note.isDefined
		def note = answer.note.get
		def datetime = answer.date.toISOString.replaceFirst("""^([0-9]+)\-([0-9]+)-([0-9]+)T([0-9]+):([0-9]+).*$""", "$3/$2/$1 – $4:$5")
		def pending = answer.answer == AnswerValue.Pending
		def star = {
			if (event.owner == answer.user) 1
			else if (user.promoted || answer.promote) 2
			else 0
		}
	}
}

@js class CalendarEventAnswers extends GtHandler {
	val calendar = service(CalendarService)
	val roster = service(RosterService)

	val event = property[Event]

	def date: String = {
		val date = event.date
		s"${ date.day }/${ date.month }/${ date.year }"
	}

	def state: String = EventState.name(event.state)

	val answers = event ~! { e =>
		if (e.owner > 0) calendar.answers.forEvent(e.id)
		else Const(Set.empty[Answer])
	} ~ { as =>
		as.groupBy(a => a.answer) withDefaultValue Set.empty
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
