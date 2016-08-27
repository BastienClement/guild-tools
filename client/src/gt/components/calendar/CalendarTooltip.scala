package gt.components.calendar

import gt.App
import gt.components.GtHandler
import gt.components.widget.RosterToon
import gt.services.CalendarService
import model.calendar._
import org.scalajs.dom.raw.CustomEvent
import rx.{Rx, Var}
import utils.DateTime
import utils.jsannotation.js
import xuen.Component

/**
  * The event tooltip from the main calendar view
  */
object CalendarTooltip extends Component[CalendarTooltip](
	selector = "calendar-event-tooltip",
	templateUrl = "/assets/imports/views/calendar.html",
	dependencies = Seq(RosterToon)
) {
	val dummyEvent: Rx[Event] = Event(0, "", "", App.user.id, DateTime.now, 0, EventVisibility.Restricted, EventState.Open)
	val dummyAnswer: Rx[Answer] = Answer(0, 0, DateTime.now, 0, None, None, false)
}

@js class CalendarTooltip extends GtHandler {
	val calendar = service(CalendarService)
	val eventid = property[Option[Int]] := None

	val event = eventid ~! (_.map(calendar.events.get).getOrElse(CalendarTooltip.dummyEvent))
	val answer = eventid ~! (_.map(calendar.answers.mineForEvent).getOrElse(CalendarTooltip.dummyAnswer))

	val visibility = event ~ (_.visibility)
	val announce = visibility ~ (_ == EventVisibility.Announce)

	val eventType = visibility ~ EventVisibility.name

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
