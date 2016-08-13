package gt.component.calendar

import gt.component.GtHandler
import gt.component.calendar.CalendarCell.CalendarCellDate
import gt.service.CalendarService
import scala.scalajs.js
import util.annotation.data
import util.jsannotation.js
import xuen.Component
import xuen.rx.Rx

object CalendarCell extends Component[CalendarCell](
	selector = "calendar-cell",
	templateUrl = "/assets/imports/views/calendar.html",
	dependencies = Seq(CalendarCellEvent)
) {
	@data case class CalendarCellDate(date: js.Date, inactive: Boolean, today: Boolean)
}

@js class CalendarCell extends GtHandler {
	val calendar = service(CalendarService)

	val inactive = attribute[Boolean]
	val today = attribute[Boolean]

	val date = property[CalendarCellDate]

	date ~> { d => inactive := d.inactive }
	date ~> { d => today := d.today }

	val mkey = date ~ { d => d.date.getFullYear() * 12 + d.date.getMonth() }
	val day = date ~ (_.date.getUTCDate)
	val key = Rx.tupled(mkey, day) ~ CalendarService.Key.tupled

	val events = key ~! calendar.events.forKey

	val slacks = key ~! calendar.slacksForKey
	val hasSlacks = slacks ~ (_.nonEmpty)
}
