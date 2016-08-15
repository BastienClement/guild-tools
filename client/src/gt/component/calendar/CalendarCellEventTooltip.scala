package gt.component.calendar

import gt.component.GtHandler
import gt.component.widget.RosterToon
import gt.service.CalendarService
import model.calendar.{Answer, AnswerValue, Event, EventVisibility}
import org.scalajs.dom.raw.CustomEvent
import rx.{Rx, Var}
import util.jsannotation.js
import xuen.Component

object CalendarCellEventTooltip extends Component[CalendarCellEventTooltip](
	selector = "calendar-cell-event-tooltip",
	templateUrl = "/assets/imports/views/calendar.html",
	dependencies = Seq(RosterToon)
)

@js class CalendarCellEventTooltip extends GtHandler {
	val calendar = service(CalendarService)
	val event = property[Event]
	val answer = property[Answer]
	val showTime = property[Boolean]
	val time = property[String]

	val eventType = event ~ (_.visibility) ~ {
		case EventVisibility.Announce => "Announce"
		case EventVisibility.Guild => "Guild event"
		case EventVisibility.Public => "Public event"
		case EventVisibility.Restricted => "Restricted event"
		case EventVisibility.Roster => "Roster event"
		case _ => "Event"
	}

	val visible = Var(false)

	val answers = Rx {
		if (visible && event.visibility != EventVisibility.Announce) {
			calendar.answers.forEvent(event.id).!
		} else {
			Set.empty[Answer]
		}
	}

	val declines = answers ~ (_.filter(_.answer == AnswerValue.Declined))
	val hasDeclines = declines ~ (_.nonEmpty)

	listen("tooltip-show", closest("gt-tooltip")) { e: CustomEvent => visible := true }
	listen("tooltip-hide", closest("gt-tooltip")) { e: CustomEvent => visible := false }
}
