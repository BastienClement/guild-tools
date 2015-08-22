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
	@Notify
	public onlines: ChatUser[] = [];

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
					console.log(type, payload);
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

	private message(news: any) {

	}
}
