package gt.component.calendar

import gt.component.GtHandler
import gt.component.widget.{GtContextMenu, GtTooltip}
import gt.service.CalendarService
import model.calendar.{Event, EventVisibility}
import util.jsannotation.js
import xuen.Component

object CalendarCellEvent extends Component[CalendarCellEvent](
	selector = "calendar-cell-event",
	templateUrl = "/assets/imports/views/calendar.html",
	dependencies = Seq(GtTooltip, GtContextMenu)
)

@js class CalendarCellEvent extends GtHandler {
	val calendar = service(CalendarService)

	val event = property[Event]
	val isAnnounce = event ~ { e => e != null && e.isAnnounce }

	val announce = attribute[Boolean]
	announce <~ isAnnounce

	val answer = event ~! (e => calendar.answers.get(app.user.id, e.id).answer)

	val hasDesc = event ~ (!_.desc.trim.isEmpty && !isAnnounce)
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
