import {Component} from "../../utils/DI";
import {Service} from "../../utils/Service";
import {Server} from "../../client/server/Server";
import {ServiceChannel} from "../../client/server/ServiceChannel";

/**
 * The stream interface returned by the server.
 */
export interface ActiveStream {
	user: number;
	live: boolean;
	progress: boolean;
	viewers: number[];
}

/**
 * Streaming service
 */
@Component
export class StreamsService extends Service {
	constructor(private server: Server) {
		super();
	}

	// Stream channel
	private channel = this.server.openServiceChannel("stream", false);

	// Actives streams
	private streams: ActiveStream[] = [];
	private streams_index = new Map<number, number>();

	/**
	 * Search a stream index by user id.
	 */
	private findStreamIndex(user: number): [number, boolean] {
		if (this.streams_index.has(user)) {
			return [this.streams_index.get(user), true];
		}
		return [this.streams.length, false];
	}

	/**
	 * Rebuild the user-stream index.
	 */
	private rebuildIndex() {
		let index = this.streams_index;
		let streams = this.streams;
		index.clear();
		for (let i = 0; i < streams.length; ++i) {
			let stream = streams[i];
			this.streams_index.set(stream.user, i);
		}
	}

	/**
	 * Full stream list received
	 */
	@ServiceChannel.Dispatch("channel", "list")
	private StreamsList(list: [number, boolean, boolean, number[]][]) {
		//noinspection TypeScriptValidateTypes
		this.streams = list.map(([user, live, progress, viewers]) => ({user, live, progress, viewers}));
		this.rebuildIndex();
		this.emit("list-update");
	}

	/**
	 * A stream was updated.
	 */
	@ServiceChannel.Dispatch("channel", "notify", true)
	private Notify(user: number, live: boolean, progress: boolean, viewers: number[]) {
		let stream = {user, live, progress, viewers};
		let [idx, found] = this.findStreamIndex(user);
		this.streams[idx] = stream;
		if (!found) this.rebuildIndex();
		this.emit("notify", stream, !found, idx);
	}

	/**
	 * A stream just went offline.
	 */
	@ServiceChannel.Dispatch("channel", "offline")
	private Offline(user: number) {
		let [idx, found] = this.findStreamIndex(user);
		if (found) {
			this.streams.splice(idx, 1);
			this.rebuildIndex();
			this.emit("offline", user, idx);
		}
	}

	/**
	 * Return the streams list
	 */
	public getStreamsList() {
		return Array.from(this.streams);
	}

	/**
	 * Request stream ticket
	 */
	public requestTicket(stream: number) {
		return this.channel.request<[string, string]>("request-ticket", stream);
	}

	/**
	 * Request own stream token
	 */
	public ownTokenVisibility() {
		return this.channel.request<[string, string, boolean]>("own-token-visibility");
	}

	/**
	 * Change the visibility status of the stream.
	 */
	public changeOwnVisibility(limited: boolean) {
		return this.channel.request<void>("change-own-visibility", limited);
	}

	/**
	 * Create a new stream token
	 */
	public createToken() {
		return this.channel.request<void>("create-token");
	}

	/**
	 * Returns the list of viewers IDs for a given stream
	 */
	public getStreamViewers(user: number): number[] {
		if (!this.streams_index.has(user)) return [];
		let idx = this.streams_index.get(user);
		let stream = this.streams[idx];
		return Array.from(stream.viewers);
	}

	/**
	 * Stream service is resumed
	 */
	private resume() {
		this.channel.open();
	}

	/**
	 * Stream service is paused
	 */
	private pause() {
		this.channel.close();
	}
}
