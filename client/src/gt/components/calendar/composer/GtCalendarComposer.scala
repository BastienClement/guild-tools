package gt.components.calendar.composer

import gt.components.calendar.GtCalendar
import gt.components.{GtHandler, View}
import gt.services.CalendarService
import utils.jsannotation.js
import xuen.Component

/**
  * The calendar composer view.
  */
object GtCalendarComposer extends Component[GtCalendarComposer](
	selector = "gt-calendar-composer",
	templateUrl = "/assets/imports/views/calendar-composer.html",
	dependencies = Seq()
) with View {
	val module = "calendar"
	val tabs: TabGenerator = GtCalendar.genTabs("composer")
}

@js class GtCalendarComposer extends GtHandler {
	val calendar = service(CalendarService)

}
