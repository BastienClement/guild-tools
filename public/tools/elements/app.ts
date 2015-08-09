import { Element, Property, Listener, Dependencies, Inject, On, Watch, Bind, PolymerElement, PolymerModelEvent } from "elements/polymer";
import { GtButton } from "elements/widgets";
import { Router } from "client/router";
import { Server } from "client/server";

@Element("gt-title-bar", "/assets/imports/app.html")
@Dependencies(GtButton)
export class GtTitleBar extends PolymerElement {
	@Inject
	@On({ "update-latency": true })
	private server: Server;

	@Property({ value: "0ms" })
	private latency: string;
	private latency_history = new Array<number>();

	private "update-latency"() {
		// Push the latency in the history array
		const history = this.latency_history;
		history.push(this.server.latency);
		if (history.length > 4) history.shift();

		// Sum and count of history values
		const acc = history.reduce((acc, l) => {
			acc.sum += l;
			acc.count++;
			return acc;
		}, { sum: 0, count: 0 });

		// Average
		const latency = acc.sum / acc.count;

		// Update the latency value
		this.latency = Math.floor(latency * 100) / 100 + "ms";
	}
}

interface SidebarIcon {
	icon: string;
	key: string;
	link: string;
}

@Element("gt-sidebar", "/assets/imports/app.html")
export class GtSidebar extends PolymerElement {
	/**
	 * List of icons to display on the sidebar
	 */
	@Property({ type: Array })
	private icons: SidebarIcon[] = [
		{ icon: "dashboard", key: "dashboard", link: "/dashboard" },
		{ icon: "account_circle", key: "profile", link: "/profile" },
		{ icon: "mail", key: "messages", link: "/messages" },
		{ icon: "today", key: "calendar", link: "/calendar" },
		{ icon: "group_work", key: "roster", link: "/roster" },
		{ icon: "forum", key: "forum", link: "/forum" },
		{ icon: "assignment_ind", key: "apply", link: "/apply" },
		{ icon: "ondemand_video", key: "streams", link: "/streams" },
		{ icon: "brush", key: "whiteboard", link: "/whiteboard" },
		{ icon: "backup", key: "drive", link: "/drive" }
	];

	/**
	 * Reference to the application router to get the
	 * current main-view module
	 */
	@Inject
	@Bind({ activeModule: "module" })
	private router: Router;

	// Current active module
	private module: string;

	/**
	 * Icon click handler
	 */
	private "icon-click"(e: PolymerModelEvent<{ link: string }>) {
		this.router.goto(e.model.item.link)
	}

	/**
	 * Check if two strings are equals
	 */
	private equals<T>(a: T, b: T) {
		return a == b;
	}
}

@Element("gt-app", "/assets/imports/app.html")
@Dependencies(GtTitleBar, GtSidebar)
export class GtApp extends PolymerElement {
	public titlebar: GtTitleBar;
	public sidebar: GtSidebar;

	private ready() {
		this.titlebar = this.$.title;
		this.sidebar = this.$.side;
	}
}
