import {Element, Property, Listener, Inject, On, Watch} from "../../polymer/Annotations";
import {PolymerElement} from "../../polymer/PolymerElement";
import {GtBox, GtAlert} from "../widgets/GtBox";
import {GtTimeago} from "../misc/GtTimeago";
import {NewsFeed, NewsData} from "../../services/NewsFeed";

interface NewsFilters {
	[key: string]: boolean;
}

@Element({
	selector: "dashboard-news-filter",
	template: "/assets/views/dashboard.html"
})
export class DashboardNewsFilter extends PolymerElement {
	@Property
	public key: string;

	@Property
	public active: boolean;

	@Property({computed: "key"})
	private get icon(): string {
		return `/assets/images/feed/${this.key.toLowerCase()}.png`;
	}

	@Listener("tap")
	private OnClick(): void {
		this.fire("toggle-filter", this.key);
	}
}

@Element({
	selector: "dashboard-news",
	template: "/assets/views/dashboard.html",
	dependencies: [DashboardNewsFilter, GtBox, GtTimeago, GtAlert]
})
export class DashboardNews extends PolymerElement {
	@Inject
	@On({"news-available": "update"})
	@Watch({"available": "UpdateAvailable"})
	private newsfeed: NewsFeed;
	public available: boolean;

	private UpdateAvailable() {
		this.available = this.newsfeed.available;
	}

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

	@Property({computed: "news.* filters.*"})
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
