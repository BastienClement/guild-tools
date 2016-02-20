import { Element, Dependencies, PolymerElement, Inject, Property, Listener, PolymerModelEvent } from "elements/polymer";
import { View, TabsGenerator } from "elements/app";
import { GtBox, GtAlert } from "elements/box";
import { GtButton } from "elements/widgets";
import { GtRosterMain } from "views/roster";
import { GtTooltip, GtContextMenu } from "elements/floating";
import { CalendarService, CalendarEvent, CalendarEventType, CalendarEventState, CalendarAnswer } from "services/calendar";

const CalendarTabs: TabsGenerator = (view, path, user) => [
	{ title: "Calendar", link: "/calendar", active: view == "views/calendar/GtCalendar" },
	{ title: "Absences", link: "/calendar/absences", active: view == "views/calendar/GtCalendarAbsences" },
	{ title: "Composer", link: "/calendar/composer", active: view == "views/calendar/GtCalendarComposer", hidden: !user.promoted }
];

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

/**
 * Calendar cell data object
 */
type CalendarDate = {
	date: Date;
	inactive?: boolean;
	today?: boolean;
};

///////////////////////////////////////////////////////////////////////////////
// <gt-calendar-overview>

@Element("gt-calendar-overview", "/assets/views/calendar.html")
@Dependencies(GtBox, GtAlert)
export class GtCalendarOverview extends PolymerElement {
}

///////////////////////////////////////////////////////////////////////////////
// <gt-calendar-cell-event-tooltip>

@Element("gt-calendar-cell-event-tooltip", "/assets/views/calendar.html")
@Dependencies(GtRosterMain)
export class GtCalendarCellEventTooltip extends PolymerElement {
	@Inject private service: CalendarService;

	@Property({ observer: "EventChanged" })
	public event: CalendarEvent;

	@Property public time: string;

	@Property({ computed: "event.type" })
	private get showTime(): boolean {
		return this.event.type != CalendarEventType.Announce;
	}

	@Property({ computed: "event.type" })
	private get eventType(): string {
		switch (this.event.type) {
			case CalendarEventType.Announce: return "Announce";
			case CalendarEventType.Guild: return "Guild event";
			case CalendarEventType.Public: return "Public event";
			case CalendarEventType.Restricted: return "Restricted event";
			case CalendarEventType.Roster: return "Roster event";
			default: return "Event";
		}
	}

	private declined: number[] = [];

	public async loadAnswers() {
		if (this.declined.length > 0) return;
		let declined: number[] = [];

		let answers = await this.service.answersForEvent(this.event.id);
		for (let answer of answers) {
			if (answer.answer == CalendarAnswer.Declined) {
				declined.push(answer.user);
			}
		}

		this.declined = declined;
	}

	private EventChanged() {
		if (this.declined.length > 0) {
			this.declined = [];
		}
	}
}

///////////////////////////////////////////////////////////////////////////////
// <gt-calendar-cell-event>

@Element("gt-calendar-cell-event", "/assets/views/calendar.html")
@Dependencies(GtTooltip, GtCalendarCellEventTooltip, GtContextMenu)
export class GtCalendarCellEvent extends PolymerElement {
	@Inject private service: CalendarService;
	@Property public event: CalendarEvent;

	@Property({ computed: "event.type", reflect: true })
	private get announce(): boolean {
		return this.event.type == CalendarEventType.Announce;
	}

	@Property({ computed: "event.type" })
	private get showTime(): boolean {
		return this.event.type != CalendarEventType.Announce;
	}

	@Property({ computed: "event.time" })
	private get time(): string {
		let time = String(this.event.time + 10000);
		return time.slice(1, 3) + ":" + time.slice(3);
	}

	@Property({ computed: "event.type event.state" })
	private get canAcceptDecline(): boolean {
		return this.event.state == CalendarEventState.Open && this.event.type != CalendarEventType.Announce;
	}

	@Listener("click")
	private OnClick() {
		this.app.router.goto(`/calendar/event/${this.event.id}`);
	}

	private ChangeAnswer(answer: CalendarAnswer) {
		console.log("change answer", answer);
	}

	private AcceptEvent() { this.ChangeAnswer(CalendarAnswer.Accepted); }
	private DeclineEvent() { this.ChangeAnswer(CalendarAnswer.Declined); }

	private ChangeState(state: CalendarEventState) {
		console.log("change state", state);
	}

	private OpenEvent() { this.ChangeState(CalendarEventState.Open); }
	private CloseEvent() { this.ChangeState(CalendarEventState.Closed); }
	private CancelEvent() { this.ChangeState(CalendarEventState.Canceled); }

	private EditEvent() {
		console.log("edit");
	}

	private DeleteEvent() {
		console.log("delete");
	}

	@Listener("floating-show")
	private OnTooltipOpen() {
		let tooltip = <GtCalendarCellEventTooltip> this.$.tooltip;
		tooltip.loadAnswers();
	}
}

///////////////////////////////////////////////////////////////////////////////
// <gt-calendar-cell>

@Element("gt-calendar-cell", "/assets/views/calendar.html")
@Dependencies(GtCalendarCellEvent, GtRosterMain, GtTooltip)
export class GtCalendarCell extends PolymerElement {
	public date: CalendarDate;

	@Property({ computed: "date.date" })
	public get day(): number {
		return this.date.date.getDate();
	}

	@Property({ computed: "date.date" })
	public get month(): number {
		return this.date.date.getMonth() + 1;
	}

	@Property({ computed: "date.date" })
	public get year(): number {
		return this.date.date.getFullYear();
	}

	@Property({ computed: "date.date date.today", reflect: true })
	public get today(): boolean {
		if (this.date.today != void 0) return this.date.today;
		let today = new Date;
		let date = this.date.date;
		return date.getDate() == today.getDate()
			&& date.getMonth() == today.getMonth()
			&& date.getFullYear() == today.getFullYear();
	}

	@Property({ computed: "date.inactive", reflect: true })
	public get inactive(): boolean {
		return this.date.inactive || false;
	}
}

///////////////////////////////////////////////////////////////////////////////
// <gt-calendar>

@View("calendar", CalendarTabs)
@Element("gt-calendar", "/assets/views/calendar.html")
@Dependencies(GtBox, GtButton, GtCalendarCell, GtCalendarOverview)
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
