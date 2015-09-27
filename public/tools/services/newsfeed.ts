import { Component } from "utils/di";
import { Service, Notify } from "utils/service";
import { Server } from "client/server";
import { Channel } from "gtp3/channel";
import { Deferred } from "utils/deferred";

/**
 * News data
 */
export interface NewsData {
	guid: string;
	link: string;
	source: string;
	tags: string;
	time: string;
	title: string;
}

/**
 * News feed data client
 */
@Component
export class NewsFeed extends Service {
	/**
	 * The newsfeed channel
	 */
	private channel = this.server.openServiceChannel("newsfeed");

	/**
	 * News data array
	 */
	private news: NewsData[] = [];
	private cache = new Set<string>();

	/**
	 * Availability flag
	 * This is set to false if the news channel cannot be opened
	 */
	@Notify
	public available: boolean = false;

	constructor(private server: Server) {
		super();
		this.channel.on("message", (type: string, payload: NewsData[]) => this.update(payload));
		this.channel.on("state", (s: boolean) => this.available = s);
	}

	/**
	 * Resume push notifications by opening a push channel with the server
	 */
	private resume(): void {
		this.channel.open();
	}

	/**
	 * Pause push notifications
	 * Closing the channel is an easy way for the server to stop broadcasting
	 * push notification to the client
	 */
	private pause(): void {
		this.channel.close();
	}

	/**
	 * Update local cache with news data from the server
	 */
	private update(data: NewsData[]): void {
		for (let i = data.length - 1; i >= 0; --i) {
			const news = data[i];
			if (!this.cache.has(news.guid)) {
				this.cache.add(news.guid);
				this.news.unshift(news);
				this.emit("news-available", news);
			}
		}
	}

	/**
	 * Request a full list of available news
	 * Usually returns an empty list on first query
	 */
	public getNews(): NewsData[] {
		return this.news.slice(0);
	}
}
