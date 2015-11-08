import { Component } from "utils/di";
import { Service } from "utils/service";
import { Server, ServiceChannel } from "client/server";

/**
 * Streaming service
 */
@Component
export class Streams extends Service {
	constructor(private server: Server) {
		super();
	}
	
	// Stream channel
	private channel = this.server.openServiceChannel("stream");
	
	// Close the channel when the streaming service is paused
	private pause() {
		this.channel.close();
	}
}
