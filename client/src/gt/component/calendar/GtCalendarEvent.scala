package gt.component.calendar

import gt.component.{GtHandler, View}
import util.jsannotation.js
import xuen.Component

/**
  * The calendar event view.
  */
object GtCalendarEvent extends Component[GtCalendarEvent](
	selector = "gt-calendar-event",
	templateUrl = "/assets/imports/views/calendar-event.html",
	dependencies = Seq()
) with View {
	val module = "calendar"
	val tabs: TabGenerator = GtCalendar.genTabs("calendar")
}

@js class GtCalendarEvent extends GtHandler {
}
