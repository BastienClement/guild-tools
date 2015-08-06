import { Element, Property, Listener, Dependencies, Inject, PolymerElement } from "elements/polymer";
import { GtButton } from "elements/widgets";
import { Router } from "client/router";

@Element("gt-title-bar", "/assets/imports/app.html")
@Dependencies(GtButton)
export class GtTitleBar extends PolymerElement {

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
		this.titlebar = this.$.titlebar;
		this.sidebar = this.$.side;
	}
}
