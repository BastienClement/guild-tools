import {Component} from "../../utils/DI";
import {Service} from "../../utils/Service";
import {Server} from "../../client/server/Server";
import {join} from "../../utils/Async";
import {ServiceChannel} from "../../client/server/ServiceChannel";
/**
 * An application meta-data
 */
export interface Apply {
	id: number;
	user: number;
	date: Date;
	stage: number;
	have_posts: boolean;
	updated: string;
}

/**
 * Message from the application feed
 */
export interface ApplyMessage {
	id: number;
	apply: number;
	user: number;
	date: Date;
	text: string;
	secret: boolean;
	system: boolean;
}

/**
 * Apply service
 */
@Component
export class ApplyService extends Service {
	constructor(private server: Server) {
		super();
	}

	// Profile channel
	private channel = this.server.openServiceChannel("apply");

	// Data cache
	private applys = new Map<number, Apply>();
	private unread = new Map<number, boolean>();

	/**
	 * Load and return the list of open applications available for
	 * the current user.
	 * This function also fetches the unread state of these applications.
	 * Result is cached until the service is paused.
	 */
	@join
	public async openApplysList() {
		let data = await this.channel.request<[Apply, boolean][]>("open-list");
		let list: number[] = [];
		for (let [apply, unread] of data) {
			let id = apply.id;
			this.applys.set(id, apply);
			this.unread.set(id, unread);
			list.push(id);
		}
		return list;
	}

	/**
	 * Check the unread state for an apply
	 * At least on call to openApplysList() is required to populate the local cache
	 * Note that the cache is cleared when the service is paused
	 */
	public unreadState(apply: number): boolean {
		return this.unread.get(apply);
	}

	/**
	 * Return application meta data (date, owner, stage, etc.)
	 * It does not return the application body
	 * This function uses the local cache if possible
	 */
	public async applyData(id: number) {
		if (!this.applys.has(id)) {
			let data = await this.channel.request<Apply>("apply-data", id);
			this.applys.set(id, data);
		}

		return this.applys.get(id);
	}

	/**
	 * Load the message feed and the body of an application
	 */
	public async applyFeedBody(id: number) {
		return await this.channel.request<[ApplyMessage[], [number, string]]>("apply-feed-body", id);
	}

	/**
	 * Send the set-seen message
	 */
	public setSeen(id: number) {
		this.UnreadUpdated(id, false);
		this.channel.send("set-seen", id);
	}

	/**
	 * Unread flag for an apply was updated
	 */
	@ServiceChannel.Dispatch("channel", "unread-updated", true)
	private UnreadUpdated(apply: number, unread: boolean) {
		this.unread.set(apply, unread);
		this.emit("unread-updated", apply, unread);
	}

	/**
	 * An apply object was updated
	 */
	@ServiceChannel.Dispatch("channel", "apply-updated")
	private ApplyUpdated(apply: Apply) {
		this.applys.set(apply.id, apply);
		this.emit("apply-updated", apply);
	}

	/**
	 * Post a new message in an application
	 */
	public async postMessage(apply: number, message: string, secret: boolean) {
		try {
			return await this.channel.request<boolean>("post-message", {apply, message, secret});
		} catch (e) {
			return false;
		}
	}

	/**
	 * New message received in an application
	 */
	@ServiceChannel.Dispatch("channel", "message-posted")
	private MessagePosted(message: ApplyMessage) {
		this.emit("message-posted", message);
	}

	/**
	 * Change application stage
	 */
	public changeApplicationStage(apply: number, stage: number) {
		return this.channel.request<void>("change-application-stage", {apply, stage});
	}

	/**
	 * Close the channel when the apply service is paused
	 * Also clear local cached data
	 */
	private pause() {
		this.channel.close();
		this.applys.clear();
		this.unread.clear();
	}
}