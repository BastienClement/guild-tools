import {PolymerElement} from "../../polymer/PolymerElement";
import {Element, Property, Inject, On} from "../../polymer/Annotations";
import {Router} from "../../client/router/Router";
import {ViewMetadata} from "../../client/router/View";

// Sidebar module icon
interface SidebarIcon {
	icon: string;
	key: string;
	link: string;
	hidden?: boolean;
}

@Element({
	selector: "gt-sidebar",
	template: "/assets/imports/app.html"
})
export class GtSidebar extends PolymerElement {
	@Property
	private icons = (<SidebarIcon[]>[
		{ icon: "widgets", key: "dashboard", link: "/dashboard" },
		{ icon: "account_circle", key: "profile", link: "/profile" },
		{ icon: "mail", key: "messages", link: "/messages" },
		{ icon: "today", key: "calendar", link: "/calendar" },
		{ icon: "supervisor_account", key: "roster", link: "/roster" },
		//{ icon: "forum", key: "forum", link: "/forum" },
		{ icon: "assignment_ind", key: "apply", link: this.app.user.roster ? "/apply" : "/apply-guest" },
		{ icon: "airplay", key: "streams", link: "/streams" },
		//{ icon: "brush", key: "whiteboard", link: "/whiteboard" },
		//{ icon: "backup", key: "drive", link: "/drive" }
	]).filter(t => !t.hidden);

	@Inject
	@On({
		"route-updated": "UpdateView"
	})
	private router: Router;

	// Current path
	private path: string;

	// Current active module
	private module: string;

	private async UpdateView() {
		// Get module name from view metadata
		let meta = Reflect.getMetadata<ViewMetadata>("view:meta", this.router.activeView);
		this.module = meta ? meta.module : null;
	}

	// Click handler for icons
	private IconClicked(e: PolymerModelEvent<{ link: string }>) {
		this.router.goto(e.model.item.link)
	}
}
