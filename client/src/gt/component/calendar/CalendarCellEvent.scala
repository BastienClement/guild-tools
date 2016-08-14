package gt.component.calendar

import gt.component.GtHandler
import model.calendar.{Event, EventVisibility}
import util.jsannotation.js
import xuen.Component

object CalendarCellEvent extends Component[CalendarCellEvent](
	selector = "calendar-cell-event",
	templateUrl = "/assets/imports/views/calendar.html",
	dependencies = Seq()
)

@js class CalendarCellEvent extends GtHandler {
	val event = property[Event]
	val isAnnounce = event ~ { e => e != null && e.isAnnounce }

	val announce = attribute[Boolean]
	announce <~ isAnnounce

	val hasDesc = event ~ (!_.desc.trim.isEmpty)
	val showTime = isAnnounce ~ (!_)

	val time = event ~ { e =>
		val base = (e.time + 10000).toString.drop(1)
		base.take(2) + ":" + base.takeRight(2)
	}

	val icon = event ~ {
		_.visibility match {
			case EventVisibility.Announce => "priority_high"
			case EventVisibility.Guild => "local_offer"
			case EventVisibility.Restricted => "vpn_key"
			case EventVisibility.Public => "public"
			case _ => ""
		}
	}
}
