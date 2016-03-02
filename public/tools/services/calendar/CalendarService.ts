import {Component} from "../../utils/DI";
import {Service} from "../../utils/Service";
import {Server} from "../../client/server/Server";
import {RBTree} from "../../utils/RBTree";
import {ServiceChannel} from "../../client/server/ServiceChannel";

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
	date: Date,
	promote: boolean;
};

export type CalendarEvent = {
	id: number;
	title: string;
	desc: string;
	date: Date;
	time: number;
	type: CalendarEventType;
	state: CalendarEventState;
	owner: number;
	answer?: CalendarAnswerData;
};

export type Slack = {
	id: number;
	user: number;
	from: Date;
	to: Date;
	reason?: string;
};

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
	 * Slack by month.
	 */
	private slacks = new RBTree<string, Slack[]>();

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
		// FIXME: maybe request every month in the interval ?
		this.requestMonth(from);
		this.requestMonth(to);

		let f_key = this.hashDate(from);
		let t_key = this.hashDate(to);

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
	 * Get a single event data
	 * @param id    the event id
	 */
	public getEvent(id: number): CalendarEvent {
		return this.index.get(id);
	}

	/**
	 * Returns slacks for a given date.
	 * Erroneous behaviour when called with from and to more than one month away.
	 * @param from
	 * @param to
	 */
	public getSlack(from: Date, to: Date = from): Slack[] {
		// FIXME: maybe request every month in the interval ?
		this.requestMonth(from);
		this.requestMonth(to);

		let f_key = this.hashMonth(from);
		let t_key = this.hashMonth(to);

		let slacks: Slack[] = this.slacks.get(f_key) || [];

		if (t_key != f_key) {
			let t_slacks = this.slacks.get(t_key);
			if (t_slacks) {
				slacks = slacks.concat(t_slacks);
			}
		}

		return slacks.filter(s => s.to >= from && s.from <= to);
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
	 * @param slacks  a list of slacks objects
	 * @param mkey    the month-key used when making the request
	 */
	@ServiceChannel.Dispatch("channel", "events", true)
	private EventsReceived(list: [CalendarEvent, CalendarAnswerData][], slacks: Slack[], mkey: string) {
		let current_date: number;
		let current_set: Array<CalendarEvent>;

		for (let [event, answer] of list) {
			// Add own answer to event object
			event.answer = answer;

			let event_time = event.date.getTime();

			// Create set if not exists
			if (event_time != current_date) {
				current_date = event_time;

				let hash = this.hashDate(event.date);
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

		if (slacks.length) {
			this.slacks.put(mkey, slacks);
		}

		this.emit("events-updated");
	}

	/**
	 * Requests answers for an event
	 * @param event
	 */
	public async answersForEvent(event: number) {
		return await this.channel.request<CalendarAnswerData[]>("event-answers", event);
	}

	/**
	 * Change an event state.
	 * @param event     an event id
	 * @param state     the new event state
	 */
	public changeEventState(event: number, state: CalendarEventState) {
		this.channel.send("change-event-state", { event, state });
	}

	/**
	 * Events data received
	 * @param event the updated event
	 */
	@ServiceChannel.Dispatch("channel", "event-updated")
	private EventUpdated(event: CalendarEvent) {
		let current = this.index.get(event.id);
		if (!current) return;

		event.answer = current.answer;
		this.index.put(event.id, event);

		let hash = this.hashDate(event.date);
		let day = this.events.get(hash);

		for (let i = 0; i < day.length; i++) {
			if (day[i].id == event.id) {
				day[i] = event;
			}
		}

		this.emit("event-updated", event);
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
