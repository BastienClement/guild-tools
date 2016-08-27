package gt.components.calendar.event

import gt.Router
import gt.components.calendar.GtCalendar
import gt.components.{GtHandler, View}
import gt.services.CalendarService
import models.calendar.{Event, EventState, EventVisibility}
import rx.{Const, Var}
import scala.concurrent.ExecutionContext.Implicits.global
import utils.DateTime
import utils.jsannotation.js
import xuen.Component

/**
  * The calendar event view.
  */
object GtCalendarEvent extends Component[GtCalendarEvent](
	selector = "gt-calendar-event",
	templateUrl = "/assets/imports/views/calendar-event.html",
	dependencies = Seq(CalendarEventInfo, CalendarEventAnswers, CalendarEventReply, CalendarEventComp)
) with View {
	val module = "calendar"
	val tabs: TabGenerator = GtCalendar.genTabs("calendar")

	/** A dummy event used while the true one is being loaded */
	val dummy = Const { Event(0, "Loading...", "", 0, DateTime.zero, 0, EventVisibility.Restricted, EventState.Open) }
}

@js class GtCalendarEvent extends GtHandler {
	val calendar = service(CalendarService)

	/** The event ID */
	val eventid = attribute[Int]

	/** A flag indicating if the event exists */
	val exists = Var(false)

	// Track eventid and request existence on change
	eventid ~> { id =>
		if (calendar.events.contains(id)) {
			exists := true
		} else {
			for (existing <- calendar.events.exists(id)) {
				exists := existing
				if (!existing) Router.goto("/calendar")
			}
		}
	}

	/** The event data */
	val event = eventid ~! { id =>
		if (calendar.events.deleted(id)) {
			Router.goto("/calendar")
			GtCalendarEvent.dummy
		} else if (exists) {
			val ev = calendar.events.get(id)
			// Filter out dummy events (Loading... is a better placeholder title)
			if (ev.owner > 0) ev else GtCalendarEvent.dummy
		} else {
			GtCalendarEvent.dummy
		}
	}

	/** Own response to the event */
	val answer = event ~! (e => calendar.answers.mineForEvent(e.id))
}
