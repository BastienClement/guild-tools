import { Element, Property, Listener, Dependencies, Inject, On, Bind, PolymerElement, PolymerModelEvent } from "elements/polymer";
import { View } from "elements/app";
import { GtBox, GtAlert } from "elements/box";
import { GtTimeago } from "elements/timeago";
import { BnetThumb } from "elements/bnet";
import { Server } from "../client/Server";
import { NewsFeed, NewsData } from "services/newsfeed";
import { ChatService, ChatMessage } from "services/chat";
import { ProfileUser } from "views/profile"

const SHOUTBOX_ROOM = 0;

interface NewsFilters {
	[key: string]: boolean;
}

///////////////////////////////////////////////////////////////////////////////
// <dashboard-news-filter>

@Element("dashboard-news-filter", "/assets/views/dashboard.html")
class DashboardNewsFilter extends PolymerElement {
	@Property
	public key: string;

	@Property
	public active: boolean;

	@Property({ computed: "key" })
	private get icon(): string {
		return `/assets/images/feed/${this.key.toLowerCase()}.png`;
	}

	@Listener("tap")
	private OnClick(): void {
		this.fire("toggle-filter", this.key);
	}
}

///////////////////////////////////////////////////////////////////////////////
// <dashboard-news>

@Element("dashboard-news", "/assets/views/dashboard.html")
@Dependencies(DashboardNewsFilter, GtBox, GtTimeago, GtAlert)
class DashboardNews extends PolymerElement {
	@Inject
	@On({ "news-available": "update" })
	@Bind({ "available": true })
	private newsfeed: NewsFeed;

	public available: boolean;
	private news: NewsData[] = this.newsfeed.getNews();

	private update(news: NewsData) {
		this.unshift("news", news);
	}

	private filters: NewsFilters;

	init() {
		try {
			let filters = localStorage.getItem("dashboard.news.filters");
			if (!filters) throw null;
			this.filters = JSON.parse(filters);
		} catch (e) {
			this.filters = {
				BLUE: true,
				MMO: true,
				WOW: true
			};
		}
	}

	@Listener("actions.toggle-filter")
	private toggleFilter(e: CustomEvent) {
		const key: string = e.detail;
		this.set(`filters.${key}`, !this.filters[key]);
		localStorage.setItem("dashboard.news.filters", JSON.stringify(this.filters));
	}

	private visible(source: string) {
		return this.filters[source];
	}

	@Property({ computed: "news.* filters.*" })
	private get noVisible(): boolean {
		return this.news.every(n => !this.filters[n.source]);
	}

	private icon(news: NewsData) {
		let icon: string;
		switch (news.source) {
			case "MMO":
				icon = "mmo";
				break;

			case "WOW":
				icon = "wow";
				break;

			case "BLUE":
				icon = news.tags.match(/EU/) ? "eu" : "us";
				break;
		}
		return `/assets/images/feed/${icon}.png`;
	}

	private open(e: PolymerModelEvent<NewsData>) {
		window.open(e.model.item.link);
	}
}

///////////////////////////////////////////////////////////////////////////////
// <dashboard-shoutbox>

@Element("dashboard-shoutbox", "/assets/views/dashboard.html")
@Dependencies()
class DashboardShoutbox extends PolymerElement {
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

///////////////////////////////////////////////////////////////////////////////
// <dashboard-onlines-user>

@Element("dashboard-onlines-user", "/assets/views/dashboard.html")
@Dependencies(BnetThumb)
class DashboardOnlinesUser extends PolymerElement {
	@Inject
	@On({ "away-changed": "UpdateAway" })
	private chat: ChatService;

	/**
	 * The user represented by this element
	 */
	@Property public user: number;

	/**
	 * Away state
	 */
	@Property public away: boolean = this.chat.isAway(this.user);

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

///////////////////////////////////////////////////////////////////////////////
// <dashboard-onlines>

@Element("dashboard-onlines", "/assets/views/dashboard.html")
@Dependencies(DashboardOnlinesUser)
class DashboardOnlines extends PolymerElement {
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

///////////////////////////////////////////////////////////////////////////////
// <gt-dashboard>

@View("dashboard", () => [{ title: "Dashboard", link: "/dashboard", active: true }])
@Element("gt-dashboard", "/assets/views/dashboard.html")
@Dependencies(DashboardNews, DashboardShoutbox, DashboardOnlines, ProfileUser)
export class GtDashboard extends PolymerElement {
	@Inject
	private server: Server;
}
