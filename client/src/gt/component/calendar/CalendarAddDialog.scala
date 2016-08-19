package gt.component.calendar

import gt.component.GtHandler
import gt.component.widget.form._
import gt.component.widget.{GtBox, GtDialog, GtTooltip}
import gt.service.CalendarService
import model.calendar.{Event, EventState, EventVisibility}
import org.scalajs.dom.raw.MouseEvent
import rx.{Rx, Var}
import scala.scalajs.js
import util.DateTime
import util.DateTime.Units
import util.jsannotation.js
import xuen.Component

object CalendarAddDialog extends Component[CalendarAddDialog](
	selector = "calendar-add-dialog",
	templateUrl = "/assets/imports/views/calendar.html",
	dependencies = Seq(GtBox, GtButton, GtInput, GtTextarea, GtCheckbox, GtTooltip)
)

@js class CalendarAddDialog extends GtHandler {
	val calendar = service(CalendarService)
	val now = new js.Date()

	implicit class TrueMod(private val a: Int) {
		@inline final def %% (b: Int): Int = ((a % b) + b) % b
	}

	val currentMonthName = GtCalendar.monthName(now.getMonth())
	val currentYear = now.getFullYear()
	val currentMonth = now.getMonth() + 1
	val currentDay = now.getDate()
	val currentMonthPadding = 0 until (new js.Date(now.getFullYear(), now.getMonth(), 1).getDay() - 1) %% 7
	val currentMonthDays = 1 to new js.Date(now.getFullYear(), now.getMonth() + 1, 0).getDate()
	val lowLimit = if (now.getHours() < 6) currentDay - 1 else currentDay

	val nextMonthName = GtCalendar.monthName((now.getMonth() + 1) % 12)
	val nextYear = new js.Date(now.getFullYear(), now.getMonth() + 1, 1).getFullYear()
	val nextMonth = new js.Date(now.getFullYear(), now.getMonth() + 1, 1).getMonth() + 1
	val nextMonthPadding = 0 until (new js.Date(now.getFullYear(), now.getMonth() + 1, 1).getDay() - 1) %% 7
	val nextMonthDays = 1 to new js.Date(now.getFullYear(), now.getMonth() + 2, 0).getDate()

	val step = Var[Int]

	val days = Var(Set.empty[DateTime])
	val canGoNext = days ~ (_.nonEmpty)
	var lastSelection: Option[DateTime] = None

	def select(day: Int, month: Int, year: Int, ev: MouseEvent): Unit = Rx.atomically {
		val date = DateTime(year, month, day)
		if (app.user.promoted) {
			(ev.shiftKey, ev.ctrlKey, lastSelection) match {
				case (true, _, Some(last)) => selectRange(last, date)
				case (_, true, _) if days.contains(date) => days ~= (_ - date)
				case (_, true, _) => days ~= (_ + date)
				case _ => days ~= (_.empty + date)
			}
			lastSelection = Some(date)
		} else {
			days ~= (_.empty + date)
		}
	}

	def selectRange(from: DateTime, to: DateTime): Unit = {
		val next: DateTime => DateTime =
			if (from < to) (dt: DateTime) => dt + 1.day
			else (dt: DateTime) => dt - 1.day

		var date = from
		while (date != to) {
			date = next(date)
			days ~= (_ + date)
		}
	}

	def selected(day: Int, month: Int, year: Int): Boolean = {
		days.contains(DateTime(year, month, day))
	}

	val eventTitle = Var[String]("")
	val eventDesc = Var[String]("")

	val defaultVisibility = if (app.user.promoted) EventVisibility.Roster else EventVisibility.Restricted
	val eventVisibility = Var[Int](defaultVisibility)

	val eventHours = Var(0)
	val eventMinutes = Var(0)
	val clockMinutes = Var(false)

	val canCreate = eventTitle ~ (_.trim.nonEmpty)

	def setHours(hours: Int): Unit = {
		eventHours := hours
		clockMinutes := true
	}

	def setMinutes(minutes: Int): Unit = eventMinutes := minutes

	def create(): Unit = {
		val template = Event(0, eventTitle.trim, eventDesc, 0, DateTime.now, eventHours * 100 + eventMinutes, eventVisibility, EventState.Open)
		calendar.createEvent(template, days)
		hide()
	}

	def show(): Unit = {
		step := 1
		days ~= (_.empty)
		lastSelection = None
		eventTitle := ""
		eventVisibility := defaultVisibility
		eventHours := 0
		eventMinutes := 0
		clockMinutes := false
		closest("gt-dialog").asInstanceOf[GtDialog].show()
	}

	def hide(): Unit = closest("gt-dialog").asInstanceOf[GtDialog].hide()
}
