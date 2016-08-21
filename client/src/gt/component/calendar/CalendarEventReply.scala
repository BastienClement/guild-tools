package gt.component.calendar

import gt.component.GtHandler
import gt.component.widget.GtBox
import gt.component.widget.form.{GtCheckbox, GtForm, GtInput}
import model.calendar.{Event, EventState}
import util.jsannotation.js
import xuen.Component

object CalendarEventReply extends Component[CalendarEventReply](
	selector = "calendar-event-reply",
	templateUrl = "/assets/imports/views/calendar-event.html",
	dependencies = Seq(GtBox, GtForm, GtCheckbox, GtInput)
)

@js class CalendarEventReply extends GtHandler {
	val event = property[Event]

	def date: String = {
		val date = event.date
		s"${ date.day }/${ date.month }/${ date.year }"
	}

	def state: String = EventState.name(event.state)
}
