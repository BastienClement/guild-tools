import {TabsGenerator, View} from "../../client/router/View";
import {PolymerElement} from "../../polymer/PolymerElement";
import {GtButton} from "../widgets/GtButton";
import {GtBox} from "../widgets/GtBox";
import {Property, Element} from "../../polymer/Annotations";
import {CalendarCell} from "./CalendarCell";
import {CalendarOverview} from "./CalendarOverview";

const CalendarTabs: TabsGenerator = (view, path, user) => [
	{ title: "Calendar", link: "/calendar", active: view == "gt-calendar" },
	{ title: "Absences", link: "/calendar/absences", active: view == "gt-calendar-absences" },
	{ title: "Composer", link: "/calendar/composer", active: view == "gt-calender-composer", hidden: !user.promoted }
];

/**
 * Calendar cell data object
 */
export type CalendarDate = {
	date: Date;
	inactive?: boolean;
	today?: boolean;
};

const MONTH_NAME = [
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
];

@View("calendar", CalendarTabs)
@Element({
	selector: "gt-calendar",
	template: "/assets/views/calendar.html",
	dependencies: [GtBox, GtButton, CalendarCell, CalendarOverview]
})
export class GtCalendar extends PolymerElement {
	/**
	 * Current calendar page [month, year]
	 */
	private page: [number, number];

	/**
	 * Today
	 */
	private today: Date;

	/**
	 * The current month title
	 */
	@Property({ computed: "page" })
	public get title(): string {
		let [month, year] = this.page;
		return MONTH_NAME[month] + " " + year;
	}

	/**
	 * The calendar layout
	 */
	@Property({ computed: "page" })
	public get layout(): CalendarDate[][] {
		let [month, year] = this.page;

		let last_month = new Date(year, month, 0);
		let days_in_last_month = last_month.getDate();
		let last_month_id = last_month.getMonth();
		let last_month_year = last_month.getFullYear();

		let next_month = new Date(year, month + 1, 1);
		let next_month_id = next_month.getMonth();
		let next_month_year = next_month.getFullYear();

		let days_in_month = new Date(year, month + 1, 0).getDate();
		let first_month_day = (new Date(year, month, 1).getDay() + 6) % 7;

		let today = this.today = new Date();
		let today_day = (today.getDay() + 6) % 7;
		let day_in_lockout = (today_day + 5) % 7;

		var lockout_start = new Date(today.getFullYear(), today.getMonth(), today.getDate() - day_in_lockout);
		var lockout_end   = new Date(today.getFullYear(), today.getMonth(), today.getDate() + (6 - day_in_lockout));

		let layout = <CalendarDate[][]> [];

		for (let r = 0; r < 6; r++) {
			let row = <CalendarDate[]> [];
			for (let c = 0; c < 7; c++) {
				let cell_day = 7 * r + c - first_month_day + 1;
				let cell_month = month;
				let cell_year = year;

				if (cell_day < 1) {
					cell_day += days_in_last_month;
					cell_month = last_month_id;
					cell_year = last_month_year;
				} else if (cell_day > days_in_month) {
					cell_day -= days_in_month;
					cell_month = next_month_id;
					cell_year = next_month_year;
				}

				let day_date = new Date(cell_year, cell_month, cell_day);

				row.push({
					date: day_date,
					inactive: !(day_date >= lockout_start && day_date <= lockout_end),
					today: (
					cell_year == today.getFullYear()
					&& cell_month == today.getMonth()
					&& cell_day == today.getDate())
				});
			}
			layout.push(row);
		}

		return layout;
	}

	/**
	 * Default month is current month
	 */
	ready() {
		this.CurrentMonth();
	}

	/**
	 * Paginate to next month
	 */
	public NextMonth(): void {
		let [month, year] = this.page;
		month += 1;
		if (month > 11) {
			month = 0;
			year += 1;
		}
		this.page = [month, year];
	}

	/**
	 * Paginate to previous month
	 */
	public PrevMonth(): void {
		let [month, year] = this.page;
		month -= 1;
		if (month < 0) {
			month = 11;
			year -= 1;
		}
		this.page = [month, year];
	}

	/**
	 * Paginate to current month
	 */
	public CurrentMonth(): void {
		let now = new Date();
		this.page = [now.getMonth(), now.getFullYear()];
	}
}
