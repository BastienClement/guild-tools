package gt.components.calendar

import gt.components.GtHandler
import gt.components.widget.GtBox
import gt.services.CalendarService
import models.calendar.Event
import utils.jsannotation.js
import xuen.Component

object CalendarEventComp extends Component[CalendarEventComp](
	selector = "calendar-event-comp",
	templateUrl = "/assets/imports/views/calendar-event.html",
	dependencies = Seq(GtBox)
)

@js class CalendarEventComp extends GtHandler {
	val calendar = service(CalendarService)

	val event = property[Event]
}
