import {Provider, Property, Inject, On} from "../../polymer/Annotations";
import {PolymerElement} from "../../polymer/PolymerElement";
import {CalendarEventType, CalendarEvent, CalendarService, Slack} from "./CalendarService";

/**
 * Calendar events fetcher
 */
@Provider("calendar-events")
export class CalendarEventsProvider extends PolymerElement {
	@Inject
	@On({
		"events-updated": "update",
		"event-updated": "EventUpdated"
	})
	private service: CalendarService;

	@Property({ observer: "update" })
	public date: Date;

	@Property({ notify: true })
	public events: number[];

	public async update() {
		if (await microtask, !this.date) return;
		this.events = this.service.getEvents(this.date).map(e => e.id);
	}

	private EventUpdated(event: CalendarEvent) {
		if (event.date.getTime() == this.date.getTime()) this.update();
	}
}

/**
 * Calendar event provider
 */
@Provider("calendar-event")
export class CalendarEventProvider extends PolymerElement {
	@Inject
	@On({
		"event-updated": "EventUpdated"
	})
	private service: CalendarService;

	@Property({ observer: "update" })
	public id: number;

	@Property({ notify: true })
	public event: CalendarEvent;

	public async update() {
		if (await microtask, !this.id) return;
		this.set("event", this.service.getEvent(this.id));
	}

	private EventUpdated(event: CalendarEvent) {
		if (event.id == this.event.id) this.update();
	}
}

/**
 * Calendar slacks fetcher
 */
@Provider("calendar-slacks")
export class CalendarSlacksProvider extends PolymerElement {
	@Inject
	@On({
		"events-updated": "update"
	})
	private service: CalendarService;

	@Property({ observer: "update" })
	public date: Date;

	@Property({ notify: true })
	public slacks: Slack[];

	public async update() {
		if (await microtask, !this.date) return;
		this.slacks = this.service.getSlack(this.date);
	}
}

/**
 * Translate event type to icon string
 */
@Provider("calendar-icon")
export class CalendarIconProvider extends PolymerElement {
	@Property({ observer: "update" })
	public type: CalendarEventType;

	@Property({ notify: true })
	public icon: string;

	public update() {
		this.icon = this.map(this.type);
	}

	private map(stage: number) {
		switch (stage) {
			case CalendarEventType.Announce:
				return "priority_high";
			case CalendarEventType.Guild:
				return "local_offer";
			case CalendarEventType.Public:
				return "public";
			case CalendarEventType.Restricted:
				return "vpn_key";
			default:
				return "";
		}
	}
}
