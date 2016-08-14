package gt.component.calendar

import gt.component.calendar.CalendarCell.CalendarCellDate
import gt.component.widget.GtBox
import gt.component.widget.form.GtButton
import gt.component.{GtHandler, Tab, View}
import rx.Var
import scala.scalajs.js
import util.implicits._
import util.jsannotation.js
import xuen.Component

object GtCalendar extends Component[GtCalendar](
	selector = "gt-calendar",
	templateUrl = "/assets/imports/views/calendar.html",
	dependencies = Seq(GtBox, GtButton, CalendarCell)
) with View {
	val module = "calendar"

	val tabs: TabGenerator = (selector, path, user) => Seq(
		Tab("Calendar", "/calendar", active = true),
		Tab("Slacks", "/slacks"),
		Tab("Composer", "/composer")
	)

	val monthName = Vector(
		"January",
		"February",
		"March",
		"April",
		"May",
		"June",
		"July",
		"August",
		"September",
		"October",
		"November",
		"December"
	)
}

@js class GtCalendar extends GtHandler {
	val page = {
		val now = new js.Date()
		Var((now.getMonth, now.getFullYear))
	}

	val current = page ~ { case (month, year) => s"${GtCalendar.monthName(month)} $year" }

	val layout = page ~ { case (month, year) =>
		val last_month = new js.Date(js.Date.UTC(year, month, 0))
		val next_month = new js.Date(js.Date.UTC(year, month + 1, 1))

		val days_in_month = new js.Date(js.Date.UTC(year, month + 1, 0)).getUTCDate
		val first_month_day = (new js.Date(js.Date.UTC(year, month, 1)).getUTCDay + 6) % 7

		val today = new js.Date(js.Date.now() - 14400000)
		val today_day = (today.getUTCDay + 6) % 7
		val day_in_lockout = (today_day + 5) % 7

		val lockout_start = new js.Date(js.Date.UTC(today.getUTCFullYear, today.getUTCMonth, today.getUTCDate - day_in_lockout))
		val lockout_end = new js.Date(js.Date.UTC(today.getUTCFullYear, today.getUTCMonth, today.getUTCDate + (6 - day_in_lockout)))

		for (r <- 0 until 6) yield for(c <- 0 until 7) yield {
			var cell_day = 7 * r + c - first_month_day + 1
			var cell_month = month
			var cell_year = year

			if (cell_day < 1) {
				cell_day += last_month.getUTCDate
				cell_month = last_month.getUTCMonth
				cell_year = last_month.getUTCFullYear
			} else if (cell_day > days_in_month) {
				cell_day -= days_in_month
				cell_month = next_month.getUTCMonth
				cell_year = next_month.getUTCFullYear
			}

			val day_date = new js.Date(js.Date.UTC(cell_year, cell_month, cell_day))
			CalendarCellDate(
				day_date,
				inactive = (!(day_date.dyn >= lockout_start.dyn && day_date.dyn <= lockout_end.dyn)).asInstanceOf[Boolean],
				today = cell_year == today.getUTCFullYear && cell_month == today.getUTCMonth && cell_day == today.getUTCDate
			)
		}
	}

	def nextMonth(): Unit = page ~= { case (month, year) => if (month == 11) (0, year + 1) else (month + 1, year) }
	def previousMonth(): Unit = page ~= { case (month, year) => if (month == 0) (11, year - 1) else (month - 1, year) }
	def currentMonth(): Unit = page := { val now = new js.Date(); (now.getMonth, now.getFullYear) }
}
