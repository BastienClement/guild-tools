import { PolymerElement, Provider, Inject, Property, On } from "elements/polymer";
import { Component } from "utils/di";
import { join } from "utils/async";
import { Service } from "utils/service";
import { Server, ServiceChannel } from "client/server";

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
export class Streams extends Service {
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
		this.streams = list.map(([user, live, progress, viewers]) => ({ user, live, progress, viewers }));
		this.rebuildIndex();
		this.emit("list-update");
	}
	
	/**
	 * A stream was updated.
	 */
	@ServiceChannel.Dispatch("channel", "notify", true)
	private Notify(user: number, live: boolean, progress: boolean, viewers: number[]) {
		let stream = { user, live, progress, viewers };
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
		return this.channel.request<string>("request-ticket", stream)
	}
	
	/**
	 * Request own stream token
	 */
	public ownToken() {
		return this.channel.request<string>("own-token");
	}
	
	/**
	 * Create a new stream token
	 */
	public createToken() {
		return this.channel.request<void>("create-token");
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

/**
 * Application data fetcher
 */
@Provider("streams-list")
class StreamsListProvider extends PolymerElement {
	@Inject
	@On({
		"list-update": "ListUpdate",
		"notify": "Notify",
		"offline": "Offline"
	})
	private service: Streams;
	
	@Property({ notify: true })
	public list: ActiveStream[] = this.service.getStreamsList();
	
	private ListUpdate() {
		this.list = this.service.getStreamsList();
	}
	
	private Notify(stream: ActiveStream, new_stream: Boolean, idx: number) {
		if (new_stream) this.push("list", stream);
		else this.set(`list.${idx}`, stream);
	}
	
	private Offline(user: number, idx: number) {
		this.splice(`list`, idx, 1);
	}
}