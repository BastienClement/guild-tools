import {PolymerElement} from "../../polymer/PolymerElement";
import {Element, Inject, Property} from "../../polymer/Annotations";
import {ChatService, ChatMessage} from "../../services/chat/ChatService";

const SHOUTBOX_ROOM = 0;

@Element({
	selector: "dashboard-shoutbox",
	template: "/assets/views/dashboard.html"
})
export class DashboardShoutbox extends PolymerElement {
	/**
	 * The chat service
	 */
	@Inject
	private chat: ChatService;

	/**
	 * Shoutbox backlog
	 */
	private messages: ChatMessage[];

	/**
	 * Track the readiness of the shoutbox data
	 */
	@Property({ reflect: true })
	private loading: boolean;

	private async attached() {
		// Display loading indicator
		this.loading = true;

		// Register the interest for this room
		this.chat.setInterest(SHOUTBOX_ROOM, this, true);

		// Request the shoutbox messages backlog
		const msgs = await this.chat.requestBacklog(SHOUTBOX_ROOM);

		this.messages = msgs;
		this.loading = false;
	}

	private detached() {
		this.chat.setInterest(SHOUTBOX_ROOM, this, false);
	}
}
