package gt.components.calendar

import gt.Router
import gt.components.GtHandler
import gt.components.widget.GtContextMenu
import gt.services.CalendarService
import models.calendar.{AnswerValue, Event, EventState, EventVisibility}
import org.scalajs.dom.MouseEvent
import rx.Rx
import utils.jsannotation.js
import xuen.Component

/**
  * An event in a cell of the main calendar view.
  */
object CalendarCellEvent extends Component[CalendarCellEvent](
	selector = "calendar-cell-event",
	templateUrl = "/assets/imports/views/calendar.html",
	dependencies = Seq(GtContextMenu)
)

@js class CalendarCellEvent extends GtHandler {
	val calendar = service(CalendarService)

	val event = property[Event]
	val isAnnounce = event ~ { e => e != null && e.isAnnounce }

	val announce = attribute[Boolean]
	announce <~ isAnnounce

	val answerData = event ~! (e => calendar.answers.get(app.user.id, e.id))
	val answer = answerData ~ (_.answer)

	val hasDesc = event ~ (!_.desc.trim.isEmpty && !isAnnounce)
	val showTime = isAnnounce ~ (!_)

	val icon = event ~ {
		_.visibility match {
			case EventVisibility.Announce => "priority_high"
			case EventVisibility.Guild => "local_offer"
			case EventVisibility.Restricted => "vpn_key"
			case EventVisibility.Public => "public"
			case _ => ""
		}
	}

	val canAcceptDecline = event ~ (e => e.state == EventState.Open && e.visibility != EventVisibility.Announce)
	val canEdit = Rx { app.user.promoted || event.owner == app.user.id || answerData.promote }
	val canDelete = Rx { app.user.promoted || event.owner == app.user.id }
	val canContextMenu = Rx { canAcceptDecline || canEdit }

	def acceptEvent(ev: MouseEvent): Unit = {
		calendar.changeEventAnswer(event.id, AnswerValue.Accepted)
		ev.stopPropagation()
	}

	def declineEvent(ev: MouseEvent): Unit = {
		calendar.changeEventAnswer(event.id, AnswerValue.Declined)
		ev.stopPropagation()
	}

	def openEvent(): Unit = calendar.changeEventState(event.id, EventState.Open)
	def closeEvent(): Unit = calendar.changeEventState(event.id, EventState.Closed)
	def cancelEvent(): Unit = calendar.changeEventState(event.id, EventState.Canceled)

	def editEvent(): Unit = fire("edit-event", event.!)
	def deleteEvent(): Unit = fire("show-delete-dialog", event.id)

	listen("mouseenter") { e: MouseEvent => fire("show-event-tooltip", (event.id, e)) }
	listen("mouseleave") { e: MouseEvent => fire("hide-event-tooltip") }

	listen("click") { e: MouseEvent =>
		if (event.visibility != EventVisibility.Announce) Router.goto(s"/calendar/event/${ event.id }")
	}
}
