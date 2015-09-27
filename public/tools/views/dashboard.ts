import { Element, Property, Listener, Dependencies, Inject, On, Watch, Bind, PolymerElement, PolymerModelEvent } from "elements/polymer";
import { Router, View } from "client/router";
import { NewsFeed, NewsData } from "services/newsfeed";
import { Chat, ChatMessage } from "services/chat";
import { Roster, User, Char } from "services/roster";
import { GtBox, GtAlert, GtTimeago, BnetThumb } from "elements/defs";

Router.declareTabs("dashboard", [
	{ title: "Dashboard", link: "/dashboard" }
]);

const SHOUTBOX_ROOM = 0;

interface NewsFilters {
	[key: string]: boolean;
}

@Element("dashboard-news-filter", "/assets/views/dashboard.html")
class DashboardNewsFilter extends PolymerElement {
	@Property()
	public key: string;

	@Property(Boolean)
	public active: boolean;

	@Property({ computed: "key" })
	private get icon(): string {
		return `/assets/images/feed/${this.key}.png`;
	}

	@Listener("tap")
	private click(): void {
		this.fire("toggle-filter", this.key);
	}
}

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
	
	constructor() {
		super();
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


@Element("dashboard-shoutbox", "/assets/views/dashboard.html")
@Dependencies()
class DashboardShoutbox extends PolymerElement {
	/**
	 * The chat service 
	 */
	@Inject
	private chat: Chat;
	
	/**
	 * Shoutbox backlog
	 */
	private messages: ChatMessage[];
	
	/**
	 * Track the readiness of the shoutbox data
	 */
	@Property({ type: Boolean, reflectToAttribute: true})
	private loading: boolean;
	
	private async attached() {
		// Display loading indicator
		this.loading = true;
		
		// Register the interest for this room
		this.chat.setInterest(SHOUTBOX_ROOM, this, true);
		
		// Request the shoutbox messages backlog
		const msgs = await this.chat.requestBacklog(SHOUTBOX_ROOM);
		
		console.log(msgs);
		this.messages = msgs;
		this.loading = false;
	}
	
	private detached() {
		this.chat.setInterest(SHOUTBOX_ROOM, this, false);
	}
}

@Element("dashboard-onlines-user", "/assets/views/dashboard.html")
@Dependencies(BnetThumb)    
class DashboardOnlinesUser extends PolymerElement {
	@Property({ type: Number })
	public user: number;
	
	@Property({ type: Boolean })
	public away: boolean = this.chat.isAway(this.user);

	@Inject
	@On({ "away-changed": "UpdateAway" })
	private chat: Chat;
	
	@Inject
	@On({ "char-updated": "CharUpdated", "user-updated": "UserUpdated" })
	private roster: Roster;
	
	private infos: User = this.roster.getUser(this.user);
	private main: Char = this.roster.getMainCharacter(this.user);
	
	private UpdateAway(user: number, away: boolean) {
		if (user == this.user) this.away = away;
	}
	
	private CharUpdated(char: Char) {
		if (char.id == this.main.id || (char.owner == this.user && char.main)) this.main = char;
	}
	
	private UserUpdated(user: User) {
		if (user.id == this.user) this.infos = user;
	}
}

@Element("dashboard-onlines", "/assets/views/dashboard.html")
@Dependencies(DashboardOnlinesUser)
class DashboardOnlines extends PolymerElement {
	@Inject
	@On(["connected", "disconnected"])
	private chat: Chat;
	
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

@View("dashboard", "gt-dashboard", "/dashboard")
@Dependencies(DashboardNews, DashboardShoutbox, DashboardOnlines)
export class Dashboard extends PolymerElement {}
