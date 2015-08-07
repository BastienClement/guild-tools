import { Element, Property, Listener, Dependencies, Inject, On, PolymerElement } from "elements/polymer";
import { GtButton } from "elements/widgets";
import { Router } from "client/router";
import { Server } from "client/server";

@Element("gt-title-bar", "/assets/imports/app.html")
@Dependencies(GtButton)
export class GtTitleBar extends PolymerElement {
	@Inject
	@On({ "update-latency": "late" })
	private server: Server;

	@Property({ value: "0ms" })
	private latency: string;

	private late() {
		this.latency = Math.floor(this.server.latency * 100) / 100 + "ms";
	}
}

@Element("gt-sidebar", "/assets/imports/app.html")
export class GtSidebar extends PolymerElement {
	@Property({ type: Array, value: [] })
	private icons: { icon: string; key: string; }[];

	@Inject
	private router: Router;

	private ready() {
		this.icons = [
			{ icon: "dashboard", key: "dashboard" },
			{ icon: "account_circle", key: "profile" },
			{ icon: "mail", key: "messages" },
			{ icon: "today", key: "calendar" },
			{ icon: "group_work", key: "roster" },
			{ icon: "forum", key: "forum" },
			{ icon: "assignment_ind", key: "apply" },
			{ icon: "ondemand_video", key: "streams" },
			{ icon: "brush", key: "whiteboard" },
			{ icon: "backup", key: "drive" }
		];
	}

	private isActive(key: string) {
		return false;
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
