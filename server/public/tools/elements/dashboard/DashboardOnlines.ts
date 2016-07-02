import {Element, Inject, On, Property, Listener} from "../../polymer/Annotations";
import {PolymerElement} from "../../polymer/PolymerElement";
import {BnetThumb} from "../misc/BnetThumb";
import {ChatService} from "../../services/chat/ChatService";

@Element({
	selector: "dashboard-onlines-user",
	template: "/assets/views/dashboard.html",
	dependencies: [BnetThumb]
})
export class DashboardOnlinesUser extends PolymerElement {
	@Inject
	@On({"away-changed": "UpdateAway"})
	private chat: ChatService;

	/**
	 * The user represented by this element
	 */
	@Property
	public user: number;

	/**
	 * Away state
	 */
	@Property
	public away: boolean = this.chat.isAway(this.user);

	/**
	 * The away-state of a user has changed
	 */
	private UpdateAway(user: number, away: boolean) {
		// Only update if the user is the good one
		if (user == this.user) this.away = away;
	}

	/**
	 * Click on the element navigate to the user profile
	 */
	@Listener("click")
	private OnClick() {
		if (this.user == this.app.user.id) this.app.router.goto("/profile");
		else this.app.router.goto(`/profile/${this.user}`);
	}
}

@Element({
	selector: "dashboard-onlines",
	template: "/assets/views/dashboard.html",
	dependencies: [DashboardOnlinesUser]
})
export class DashboardOnlines extends PolymerElement {
	@Inject
	@On(["connected", "disconnected"])
	private chat: ChatService;

	/**
	 * The sorted list of users used for display
	 */
	private onlines: number[] = this.chat.onlinesUsers;

	/**
	 * A new user is now connected to GT
	 */
	private connected(user: number) {
		let i = 0, l = this.onlines.length;
		while (i < l && this.onlines[i] < user) i++;
		this.splice("onlines", i, 0, user);
	}

	/**
	 * A previously connected user just disconnected
	 */
	private disconnected(user: number) {
		for (let i = 0; i < this.onlines.length; i++) {
			if (this.onlines[i] == user) {
				this.splice("onlines", i, 1);
				break;
			}
		}
	}
}
