package gt.component.calendar

import gt.component.GtHandler
import gt.component.widget.GtBox
import model.calendar.{Event, EventState}
import util.jsannotation.js
import xuen.Component

object CalendarEventAnswers extends Component[CalendarEventAnswers](
	selector = "calendar-event-answers",
	templateUrl = "/assets/imports/views/calendar-event.html",
	dependencies = Seq(GtBox)
)

@js class CalendarEventAnswers extends GtHandler {
	val event = property[Event]

	def date: String = {
		val date = event.date
		s"${ date.day }/${ date.month }/${ date.year }"
	}

	def state: String = EventState.name(event.state)
}
