import { Component } from "utils/di";
import { Service } from "utils/service";
import { Server, ServiceChannel } from "client/server";
import { Char } from "services/roster";

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
	
	// Check if a character is already registered to a user
	public checkAvailability(server: string, name: string) {
		return this.channel.request<boolean>("is-char-available", { server, name });
	}
	
	// Fetch a specific char from Battle.net
	public fetchChar(server: string, name: string) {
		return this.channel.request<Char>("fetch-char", { server, name });
	}
	
	public registerChar(server: string, name: string, role: string, owner: number) {
		return this.channel.request<void>("register-char", { server, name, role, owner });
	}
	
	// Close the channel when the profile service is paused
	private pause() {
		this.channel.close();
	}
}
