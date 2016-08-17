package gt.component.calendar

import gt.Router
import gt.component.GtHandler
import gt.component.widget.GtContextMenu
import gt.service.CalendarService
import model.calendar.{AnswerValue, Event, EventState, EventVisibility}
import org.scalajs.dom.MouseEvent
import rx.Rx
import util.jsannotation.js
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
	val canContextMenu = Rx { canAcceptDecline || canEdit }

	def acceptEvent(): Unit = calendar.changeEventAnswer(event.id, AnswerValue.Accepted)
	def declineEvent(): Unit = calendar.changeEventAnswer(event.id, AnswerValue.Declined)

	def openEvent(): Unit = calendar.changeEventState(event.id, EventState.Open)
	def closeEvent(): Unit = calendar.changeEventState(event.id, EventState.Closed)
	def cancelEvent(): Unit = calendar.changeEventState(event.id, EventState.Canceled)

	def editEvent(): Unit = {}
	def deleteEvent(): Unit = fire("show-delete-dialog", event.id)

	listen("mouseenter") { e: MouseEvent => fire("show-event-tooltip", (event.id, e)) }
	listen("mouseleave") { e: MouseEvent => fire("hide-event-tooltip") }

	listen("click") { e: MouseEvent => Router.goto(s"/calendar/event/${ event.id }") }
}
