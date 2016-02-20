import { Component } from "utils/di";
import { Service } from "utils/service";
import { Server, ServiceChannel } from "client/server";
import { Char } from "services/roster";

export type ProfileData = {
	user: number;
	realname: string;
	btag: string;
	phone: string;
	birthday: Date;
	mail: string;
	location: string;
}

/**
 * Profile service
 */
@Component
export class ProfileService extends Service {
	constructor(private server: Server) {
		super();
	}

	/**
	 * The Profile channel
	 * @type {ServiceChannel}
	 */
	private channel = this.server.openServiceChannel("profile");

	/**
	 * Check if a character is available for registration or already registered to a user
	 */
	public checkAvailability(server: string, name: string) {
		return this.channel.request<boolean>("is-char-available", { server, name });
	}

	/**
	 * Fetch a specific char from Battle.net
	 * This function is rate-limited and must not be called too often.
	 */
	public fetchChar(server: string, name: string) {
		return this.channel.request<Char>("fetch-char", { server, name });
	}

	/**
	 * Register a new character with the user's account.
	 */
	public registerChar(server: string, name: string, role: string, owner: number) {
		return this.channel.request<void>("register-char", { server, name, role, owner });
	}

	/**
	 * Retrieve a user's profile
	 */
	public userProfile(user: number) {
		return this.channel.request<ProfileData>("user-profile", { id: user });
	}

	/**
	 * Close the channel when the profile service is paused
	 */
	private pause() {
		this.channel.close();
	}
}
