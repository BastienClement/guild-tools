import {PolymerElement} from "../../polymer/PolymerElement";
import {Element, Property} from "../../polymer/Annotations";
import {GtTooltip} from "../widgets/GtTooltip";
import {CalendarDate} from "./GtCalendar";
import {CalendarCellEvent} from "./CalendarCellEvent";
import {RosterMain} from "../roster/RosterMain";

@Element({
	selector: "calendar-cell",
	template: "/assets/views/calendar.html",
	dependencies: [CalendarCellEvent, RosterMain, GtTooltip]
})
export class CalendarCell extends PolymerElement {
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
