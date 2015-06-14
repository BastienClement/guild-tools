import { Element, Property, Listener, Dependencies, PolymerElement, PolymerEvent } from "elements/polymer";
import { GtButton } from "elements/widgets";

@Element("gt-title-bar", "/assets/imports/app.html")
@Dependencies(GtButton)	
export class GtTitleBar extends PolymerElement {
	
}

@Element("gt-sidebar", "/assets/imports/app.html")
export class GtSidebar extends PolymerElement {
	@Property({ type: Array, value: [] })
	private icons: { icon: string; key: string; }[];
	
	private ready() {
		this.icons = [
			{ icon: "dashboard", key: "dashboard" },
			{ icon: "account_circle", key: "profile" },
			{ icon: "mail", key: "messages" },
			{ icon: "today", key: "calendar" },
			{ icon: "group_work", key: "roster" },
			{ icon: "forum", key: "forum" },
			{ icon: "backup", key: "drive" },
			{ icon: "ondemand_video", key: "streams" },
			{ icon: "brush", key: "whiteboard" },
			{ icon: "assignment_ind", key: "apply" },
		];
	}
}

@Element("gt-app", "/assets/imports/app.html")
@Dependencies(GtTitleBar, GtSidebar)	
export class GtApp extends PolymerElement {
	
}
