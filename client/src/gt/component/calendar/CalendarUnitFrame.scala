package gt.component.calendar

import gt.component.GtHandler
import gt.service.RosterService
import rx.Const
import util.jsannotation.js
import xuen.Component

object CalendarUnitFrame extends Component[CalendarUnitFrame](
	selector = "calendar-unit-frame",
	templateUrl = "/assets/imports/views/calendar-event.html",
	dependencies = Seq()
)

@js class CalendarUnitFrame extends GtHandler {
	val roster = service(RosterService)

	val toon = property[Int]

	val data = toon ~! { id =>
		if (id > 0) roster.toon(id)
		else Const(null)
	}

	def hasToon = toon > 0
}
