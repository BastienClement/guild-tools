package gt.component.calendar

import gt.component.GtHandler
import gt.component.widget.{GtBox, GtContextMenu, GtDialog}
import gt.service.CalendarService
import model.calendar.{Answer, Event, EventState}
import rx.Rx
import util.jsannotation.js
import xuen.Component

object CalendarEventInfo extends Component[CalendarEventInfo](
	selector = "calendar-event-info",
	templateUrl = "/assets/imports/views/calendar-event.html",
	dependencies = Seq(GtBox, GtContextMenu, GtDialog, CalendarAddDialog)
)

@js class CalendarEventInfo extends GtHandler {
	val calendar = service(CalendarService)

	val event = property[Event]
	val answer = property[Answer]

	def date: String = {
		val date = event.date
		s"${date.day}/${date.month}/${date.year}"
	}

	def state: String = EventState.name(event.state)

	def editable = Rx {
		val me = app.user
		me.promoted || me.id == event.owner || answer.promote
	}

	def openEvent(): Unit = calendar.changeEventState(event.id, EventState.Open)
	def closeEvent(): Unit = calendar.changeEventState(event.id, EventState.Closed)
	def cancelEvent(): Unit = calendar.changeEventState(event.id, EventState.Canceled)
}
