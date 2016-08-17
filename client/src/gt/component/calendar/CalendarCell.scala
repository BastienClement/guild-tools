package gt.component.calendar

import gt.component.GtHandler
import gt.component.calendar.CalendarCell.CalendarCellDate
import gt.service.CalendarService
import org.scalajs.dom._
import scala.scalajs.js
import util.annotation.data
import util.jsannotation.js
import xuen.Component

/**
  * A cell of the main calendar view.
  */
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

	val day = date ~ (_.date.getUTCDate())
	val key = date ~ { d => ((d.date.getUTCFullYear() - 2000) * 12 + d.date.getUTCMonth()) * 32 + d.date.getUTCDate() }

	val events = key ~! calendar.events.forKey

	val slacks = key ~! calendar.slacks.forKey
	val slacksCount = slacks ~ (_.size)

	listen("mouseenter", child("#slacks")) { e: MouseEvent => fire("show-slacks-tooltip", (key.!, e)) }
	listen("mouseleave", child("#slacks")) { e: MouseEvent => fire("hide-slacks-tooltip") }
}
