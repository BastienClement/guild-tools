import { Component } from "utils/di";
import { Service } from "utils/service";
import { join, synchronized, microtask } from "utils/async";
import { Server, ServiceChannel } from "client/server";
import { PolymerElement, Provider, Inject, Property, On } from "elements/polymer";
import { RBTree } from "utils/rbtree";

export const enum CalendarAnswer {
	Pending = 0,
	Accepted = 1,
	Declined = 2
}

export const enum CalendarEventState {
	Open = 0,
	Closed = 1,
	Canceled = 2
}

export const enum CalendarEventType {
	Roster = 1,
	Public = 2,
	Restricted = 3,
	Announce = 4,
	Guild = 5
}

export type CalendarAnswerData = {
	user: number;
	event: number;
	answer: CalendarAnswer;
	date: string,
	promote: boolean;
}

export type CalendarEvent = {
	id: number;
	title: string;
	desc: string;
	date: string;
	time: number;
	type: CalendarEventType;
	state: CalendarEventState;
	owner: number;
	answer?: CalendarAnswerData;
}

/**
 * Calendar service
 */
@Component
export class CalendarService extends Service {
	constructor(private server: Server) {
		super();
	}

	/**
	 * Calendar channel
	 */
	private channel = this.server.openServiceChannel("calendar", false);

	/**
	 * Compute a number value for a date.
	 * In practice, returns the number of milliseconds since epoch.
	 * @param date  the date
	 */
	private hashDate(date: Date): number {
		return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
	}

	/**
	 * Compute the month code for a date.
	 * @param date  the date
	 */
	private hashMonth(date: Date): string {
		return date.toISOString().slice(0, 7);
	}

	/**
	 * Events index (id to event).
	 */
	private index = new RBTree<number, CalendarEvent>();

	/**
	 * Events by days.
	 */
	private events = new RBTree<number, CalendarEvent[]>();

	/**
	 * Are the events for a particular day sorted ?
	 */
	private sorted = new Map<number, boolean>();

	/**
	 * Set of requested month.
	 */
	private requested = new Set<string>();

	/**
	 * Request events for a given month.
	 * Loading is month-based and will not be performed if data is already available.
	 * @param date  the month to load
	 */
	private requestMonth(date: Date) {
		let mkey = this.hashMonth(date);
		if (this.requested.has(mkey)) return;
		else this.requested.add(mkey);
		this.channel.send("request-events", mkey);
	}

	/**
	 * Returns events for a given date.
	 * Erroneous behaviour when called with from and to more than one month away.
	 * @param from
	 * @param to
	 */
	public getEvents(from: Date, to: Date = from): CalendarEvent[] {
		let f_key = this.hashDate(from);
		let t_key = this.hashDate(to);

		// FIXME: maybe request every month in the interval ?
		this.requestMonth(from);
		this.requestMonth(to);

		let res = <CalendarEvent[]> [];

		for (let [key, events] of this.events.search(f_key, t_key)) {
			if (!this.sorted.get(key)) {
				this.sortEvents(events);
				this.sorted.set(key, true);
			}

			for (let event of events) {
				res.push(event);
			}
		}

		return res;
	}

	/**
	 * Sort events for a given date
	 */
	private sortEvents(list: Array<CalendarEvent>) {
		list.sort((a: CalendarEvent, b: CalendarEvent) => {
			if ((a.type == CalendarEventType.Announce || b.type == CalendarEventType.Announce) && a.type !== b.type)
				return (a.type == CalendarEventType.Announce) ? -1 : 1;

			let a_time = (a.time < 600) ? a.time + 2400 : a.time;
			let b_time = (b.time < 600) ? b.time + 2400 : b.time;
			if (a_time !== b_time) return b_time - a_time;

			return a.id - b.id;
		});
	}

	/**
	 * Events data received
	 * @param list    a list of events
	 */
	@ServiceChannel.Dispatch("channel", "events")
	private EventsReceived(list: [CalendarEvent, CalendarAnswerData][]) {
		let current_date: string;
		let current_set: Array<CalendarEvent>;

		for (let [event, answer] of list) {
			// Add own answer to event object
			event.answer = answer;

			// Create set if not exists
			if (event.date != current_date) {
				current_date = event.date;
				let hash = this.hashDate(new Date(event.date));
				this.sorted.set(hash, false);
				current_set = this.events.get(hash);
				if (!current_set) {
					current_set = [];
					this.events.put(hash, current_set);
				}
			}

			// Add event to set
			current_set.push(event);
			this.index.put(event.id, event);
			this.emit("event-updated", event);
		}

		this.emit("events-updated");
	}

	/**
	 * Close the channel when the apply service is paused.
	 * Also clear every cached event since we will no longer be informed if an event is updated.
	 */
	private pause() {
		this.index.clear();
		this.events.clear();
		this.requested.clear();
		this.channel.close();
	}
}

/**
 * Application data fetcher
 */
@Provider("calendar-events")
class CalendarEventProvider extends PolymerElement {
	@Inject
	@On({
		"events-updated": "update"
	})
	private service: CalendarService;

	@Property({ observer: "update" })
	public date: Date;

	@Property({ notify: true })
	public events: CalendarEvent[];

	public async update() {
		if (await microtask, !this.date) return;
		this.events = this.service.getEvents(this.date);
	}

	/*private ApplyUpdated(apply: Apply) {
		if (apply.id == this.apply) this.update();
	}*/
}

/**
 * Translate event type to icon string
 */
@Provider("calendar-icon")
class CalendarIconProvider extends PolymerElement {
	@Property({ observer: "update" })
	public type: CalendarEventType;

	@Property({ notify: true })
	public icon: string;

	public update() {
		this.icon = this.map(this.type);
	}

	private map(stage: number) {
		switch (stage) {
			case CalendarEventType.Announce: return "priority_high";
			case CalendarEventType.Guild: return "local_offer";
			case CalendarEventType.Public: return "public";
			case CalendarEventType.Restricted: return "vpn_key";
			default: return "";
		}
	}
}
