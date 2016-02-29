import { Component } from "../utils/DI";
import { Service, Notify } from "../utils/Service";
import { Server, ServiceChannel } from "../client/Server";

export interface ChatUser {
	name: string;
	color: string;
	group: number;
	status: number;
}

export interface ChatMessage {
	id: number;
	room: number;
	user?: number;
	from: string;
	text: string;
	date: Date;
}

/**
 * Chat data client
 */
@Component
export class ChatService extends Service {
	// List of onlines user
	private onlines = new Map<number, boolean>();
	private channel = this.server.openServiceChannel("chat", false);

	@Notify
	@ServiceChannel.ReflectState("channel")
	public available: boolean = false;

	constructor(private server: Server) {
		super();

		this.channel.on("reset", () => {
			// TODO: sync interested flags
		});
	}

	// Full update of the online user list
	// Check the cached data to find who is now connected or
	// disconnected in order to emit appropriate events
	@ServiceChannel.Dispatch("channel", "onlines")
	private UpdateOnlines(users: [number, boolean][]) {
		// Currently onlines users (cached)
		const onlines = this.onlines;

		// User listed in the server message
		const users_received = new Set<number>();

		// Loop over the list just received of connected user
		for (let [user, away] of users) {
			users_received.add(user);

			if (onlines.has(user)) {
				// User is already present in the local cache
				// Check old away status and trigger an event if changed
				const old_status = onlines.get(user);
				if (old_status != away) {
					onlines.set(user, away);
					this.emit("away-changed", user, away);
				}
			} else {
				// User was not in the local cache, send a connected event
				onlines.set(user, away);
				this.emit("connected", user);
			}
		}

		// Now check the local cache and find users who were not in
		// the server sent list, these users are disconnected
		for (let user of Array.from(onlines.keys())) {
			if (!users_received.has(user)) {
				onlines.delete(user);
				this.emit("disconnected", user);
			}
		}
	}

	// An user just logged in
	@ServiceChannel.Dispatch("channel", "connected")
	private UserConnected(user: number) {
		this.onlines.set(user, false);
		this.emit("connected", user);
	}

	// A user was just disconnected
	@ServiceChannel.Dispatch("channel", "disconnected")
	private UserDisconnected(user: number) {
		this.onlines.delete(user);
		this.emit("disconnected", user);
	}

	// Change in the away state of a user
	@ServiceChannel.Dispatch("channel", "away-changed", true)
	private AwayChanged(user: number, away: boolean) {
		this.onlines.set(user, away);
		this.emit("away-changed", user, away);
	}

	// Generate a list of user id currently connected to the chat service
	public get onlinesUsers(): number[] {
		return Array.from(this.onlines.keys());
	}

	public isAway(user: number): boolean {
		return this.onlines.get(user);
	}

	// Request a backlog of messages from a chat room
	public requestBacklog(room: number, upper?: number): Promise<ChatMessage[]> {
		return this.channel.request("room-backlog", { room, upper });
	}

	// Current interests in room events
	// This map binds a channel number to a list of interested receiver
	private interests = new Map<number, Set<any>>();

	// Define the interesting chat rooms
	public setInterest(room: number, owner: any, interested: boolean) {
		// Local binding cache for this room
		let bindings = this.interests.get(room);

		// This room was not in the interests cache
		if (!bindings) {
			bindings = new Set<any>();
			this.interests.set(room, bindings);
		}

		// Set or reset interest
		if (interested) {
			bindings.add(owner);
		} else {
			bindings.delete(owner);
		}

		// We only send a message to the server if the number of bindings is 1 or 0
		// In every other cases, the interest state for this room cannot have changed
		if (bindings.size < 2 && this.channel) {
			this.channel.send("set-interest", { room, interested: bindings.size > 0 });
		}
	}
}
