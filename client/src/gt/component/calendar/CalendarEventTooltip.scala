package gt.component.calendar

import gt.component.GtHandler
import gt.component.widget.RosterToon
import gt.service.CalendarService
import model.calendar.{Answer, AnswerValue, Event, EventVisibility}
import org.scalajs.dom.raw.CustomEvent
import rx.{Rx, Var}
import util.DateTime
import util.jsannotation.js
import xuen.Component

object CalendarEventTooltip extends Component[CalendarEventTooltip](
	selector = "calendar-event-tooltip",
	templateUrl = "/assets/imports/views/calendar.html",
	dependencies = Seq(RosterToon)
) {
	val dummyEvent: Rx[Event] = Event(0, "", "", 2, DateTime.now, 0, 0, 0)
	val dummyAnswer: Rx[Answer] = Answer(0, 0, DateTime.now, 0, None, None, false)
}

@js class CalendarEventTooltip extends GtHandler {
	val calendar = service(CalendarService)
	val eventid = property[Option[Int]] := None

	val event = eventid ~! (_.map(calendar.events.get).getOrElse(CalendarEventTooltip.dummyEvent))
	val answer = eventid ~! (_.map(calendar.answers.myAnswerForEvent).getOrElse(CalendarEventTooltip.dummyAnswer))

	val visibility = event ~ (_.visibility)
	val announce = visibility ~ (_ == EventVisibility.Announce)

	val eventType = visibility ~ {
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
