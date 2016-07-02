import {Element, Inject, Property, Listener} from "../../polymer/Annotations";
import {PolymerElement} from "../../polymer/PolymerElement";
import {
	CalendarService,
	CalendarEvent,
	CalendarEventType,
	CalendarAnswer,
	CalendarAnswerData,
	CalendarEventState
} from "../../services/calendar/CalendarService";
import {GtContextMenu} from "../widgets/GtContextMenu";
import {GtTooltip} from "../widgets/GtTooltip";
import {RosterMain} from "../roster/RosterMain";

///////////////////////////////////////////////////////////////////////////////
// <gt-calendar-cell-event-tooltip>

@Element({
	selector: "calendar-cell-event-tooltip",
	template: "/assets/views/calendar.html",
	dependencies: [RosterMain]
})
export class CalendarCellEventTooltip extends PolymerElement {
	@Inject
	private service: CalendarService;

	@Property({ observer: "EventChanged" })
	public event: CalendarEvent;

	@Property
	public time: string;

	@Property({ computed: "event.type" })
	private get showTime(): boolean {
		return this.event.type != CalendarEventType.Announce;
	}

	@Property({ computed: "event.type" })
	private get eventType(): string {
		switch (this.event.type) {
			case CalendarEventType.Announce:
				return "Announce";
			case CalendarEventType.Guild:
				return "Guild event";
			case CalendarEventType.Public:
				return "Public event";
			case CalendarEventType.Restricted:
				return "Restricted event";
			case CalendarEventType.Roster:
				return "Roster event";
			default:
				return "Event";
		}
	}

	private declined: number[] = [];

	public async loadAnswers() {
		if (this.event.type == CalendarEventType.Announce) return;
		let declined: number[] = [];

		let answers = await this.service.answersForEvent(this.event.id);
		for (let answer of answers) {
			if (answer.answer == CalendarAnswer.Declined) {
				declined.push(answer.user);
			}
		}

		this.declined = declined;
	}

	private EventChanged(current: CalendarEvent, previous: CalendarEvent) {
		if (previous && previous.id == current.id) return;
		if (this.declined.length > 0) {
			this.declined = [];
		}
	}
}

///////////////////////////////////////////////////////////////////////////////
// <gt-calendar-cell-event>

@Element({
	selector: "calendar-cell-event",
	template: "/assets/views/calendar.html",
	dependencies: [GtTooltip, CalendarCellEventTooltip, GtContextMenu]
})
export class CalendarCellEvent extends PolymerElement {
	@Inject
	private service: CalendarService;
	@Property
	public id: number;
	@Property
	private event: CalendarEvent;

	@Property({ computed: "event.type", reflect: true })
	private get announce(): boolean {
		return this.event.type == CalendarEventType.Announce;
	}

	@Property({ computed: "event.type" })
	private get showTime(): boolean {
		return this.event.type != CalendarEventType.Announce;
	}

	@Property({ computed: "event" })
	private get answer(): CalendarAnswerData {
		return this.event.answer || null;
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

	@Property({ computed: "event.owner answer" })
	private get canEdit(): boolean {
		return this.app.user.promoted || this.event.owner == this.app.user.id ||
			(this.answer && this.answer.promote);
	}

	@Property({ computed: "canAcceptDecline canEdit" })
	private get canContextMenu(): boolean {
		return this.canAcceptDecline || this.canEdit;
	}

	@Listener("click")
	private OnClick() {
		this.app.router.goto(`/calendar/event/${this.event.id}`);
	}

	private ChangeAnswer(answer: CalendarAnswer) {
		console.log("change answer", answer);
	}

	private AcceptEvent() {
		this.ChangeAnswer(CalendarAnswer.Accepted);
	}

	private DeclineEvent() {
		this.ChangeAnswer(CalendarAnswer.Declined);
	}

	private ChangeState(state: CalendarEventState) {
		this.service.changeEventState(this.event.id, state);
	}

	private OpenEvent() {
		this.ChangeState(CalendarEventState.Open);
	}

	private CloseEvent() {
		this.ChangeState(CalendarEventState.Closed);
	}

	private CancelEvent() {
		this.ChangeState(CalendarEventState.Canceled);
	}

	private EditEvent() {
		console.log("edit");
	}

	private DeleteEvent() {
		if (confirm("Are you sure ?")) {
			console.log("delete");
		}
	}

	@Listener("floating-show")
	private OnTooltipOpen() {
		let tooltip = <CalendarCellEventTooltip> this.$.tooltip;
		tooltip.loadAnswers();
	}
}
