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

/**
 * News feed data client
 */
@Component
export class Chat extends PausableEventEmitter {
	/**
	 * The newsfeed channel
	 */
	private channel: Channel;

	/**
	 * List of onlines user
	 */
	private onlines = new Map<number, boolean>();

	constructor(private server: Server) {
		super();
		this.connect();
	}

	private connect() {
		if (this.channel) return;
		this.server.openChannel("chat").then(
			(chan) => {
				this.channel = chan;

				chan.on("message", (type: string, payload: any) => {
					switch (type) {
						case "onlines": return this.updateOnlines(payload);
						case "connected": return this.userConnected(payload);
						case "disconnected": return this.userDisconnected(payload);
						default:
							console.info("Unknown message received on chat channel", payload);
					}
				});

				chan.on("close", () => this.disconnected());
			},
			(e) => {
				console.error("Unable to open Chat channel");
			}
		);
	}

	private disconnected() {
		this.channel = null;
		setTimeout(() => this.connect(), 3000);
	}

	private updateOnlines(users: [number, boolean][]) {
		const onlines = this.onlines;
		const users_set = new Set<number>();

		for (let [user, away] of users) {
			users_set.add(user);
			if (onlines.has(user)) {
				const old_status = onlines.get(user);
				if (old_status != away) {
					onlines.set(user, away);
					this.emit("away-changed", user, away);
				}
			} else {
				onlines.set(user, away);
				this.emit("connected", user);
			}
		}

		for (let user of Array.from(onlines.keys())) {
			if (!users_set.has(user)) {
				onlines.delete(user);
				this.emit("disconnected", user);
			}
		}
	}

	private userConnected(user: number) {
		this.onlines.set(user, false);
		this.emit("connected", user);
	}

	private userDisconnected(user: number) {
		this.onlines.delete(user);
		this.emit("disconnected", user);
	}

	public getOnlinesUsers(): number[] {
		return Array.from(this.onlines.keys());
	}
}
