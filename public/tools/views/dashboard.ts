import { Element, Property, Listener, Dependencies, Inject, On, Watch, Bind, PolymerElement, PolymerModelEvent } from "elements/polymer";
import { Router, View } from "client/router";
import { NewsFeed, NewsData } from "services/newsfeed";
import { GtBox, GtAlert, GtTimeago } from "elements/defs";

Router.declareTabs("dashboard", [
	{ title: "Dashboard", link: "/dashboard" }
]);

interface Filters {
	[key: string]: boolean;
}

@Element("dashboard-news-filter", "/assets/views/dashboard.html")
class DashboardNewsFilter extends PolymerElement {
	@Property
	public key: string;

	@Property(Boolean)
	public active: boolean;

	@Property({ computed: "key" })
	private get icon() {
		return `/assets/images/feed/${this.key}.png`;
	}

	@Listener("tap")
	private click() {
		this.fire("toggle-filter", this.key);
	}
}

@Element("dashboard-news", "/assets/views/dashboard.html")
@Dependencies(DashboardNewsFilter, GtBox, GtTimeago, GtAlert)
class DashboardNews extends PolymerElement {
	@Inject
	@On({ "news-available": "update" })
	private newsfeed: NewsFeed;

	private news: NewsData[] = this.newsfeed.getNews();

	private update(news: NewsData) {
		this.unshift("news", news);
	}

	private filters: Filters = {
		BLUE: true,
		MMO: true,
		WOW: true
	};

	@Listener("actions.toggle-filter")
	private toggleFilter(e: CustomEvent) {
		const key: string = e.detail;
		this.set(`filters.${key}`, !this.filters[key]);
	}

	private visible(source: string) {
		return this.filters[source];
	}

	@Property({ computed: "news.* filters.*" })
	private get noVisible() {
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

@View("dashboard", "gt-dashboard", "/dashboard")
@Dependencies(DashboardNews)
export class Dashboard extends PolymerElement {
}
