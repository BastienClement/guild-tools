import { Component } from "utils/di";
import { PausableEventEmitter, Notify } from "utils/eventemitter";
import { Server } from "client/server";
import { Channel } from "gtp3/channel";
import { Deferred } from "utils/deferred";

export interface ChatUser {
	name: string;
	color: string;
	group: number;
	status: number;
}

export interface ChatMessage {
	room: number;
}

/**
 * Chat data client
 */
@Component
export class Chat extends PausableEventEmitter {
	// List of onlines user
	private onlines = new Map<number, boolean>();
	private channel = this.server.openServiceChannel("chat");
	
	@Notify
	public available: boolean = false;    

	constructor(private server: Server) {
		super();
		
		this.channel.on("message", (type: string, payload: any) => {
			switch (type) {
				case "onlines": return this.updateOnlines(payload);
				case "connected": return this.userConnected(payload);
				case "disconnected": return this.userDisconnected(payload);
				default:
					console.info("Unknown message received on chat channel:", type, payload);
			}
		});
		
		this.channel.on("reset", () => {
			// TODO: sync interested flags
		});
		
		this.channel.on("state", (s: boolean) => this.available = s);
	}

	// Full update of the online user list
	// Check the cached data to find who is now connected or 
	// disconnected in order to emit appropriate events
	private updateOnlines(users: [number, boolean][]) {
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
	private userConnected(user: number) {
		this.onlines.set(user, false);
		this.emit("connected", user);
	}

	// A user was just disconnected
	private userDisconnected(user: number) {
		this.onlines.delete(user);
		this.emit("disconnected", user);
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
