import { Component } from "utils/di";
import { Service, Notify } from "utils/service";
import { Server, ServiceChannel } from "client/server";
import { Channel } from "gtp3/channel";
import { Deferred } from "utils/deferred";

/**
 * Profile service
 */
@Component
export class Profile extends Service {
	constructor(private server: Server) {
		super();
	}
	
	// Profile channel
	private channel = this.server.openServiceChannel("profile");
}
