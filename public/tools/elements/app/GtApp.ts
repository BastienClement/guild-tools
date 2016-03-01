import {PolymerElement} from "../../polymer/PolymerElement";
import {Element, Property, Inject, On} from "../../polymer/Annotations";
import {GtView} from "./GtView";
import {GtSidebar} from "./GtSidebar";
import {GtTitleBar} from "./GtTitleBar";
import {Server} from "../../client/server/Server";
import {RosterService} from "../../services/roster/Roster";
import {GtDialog} from "../widgets/GtDialog";

@Element({
	selector: "gt-app",
	template: "/assets/imports/app.html"
})
export class GtApp extends PolymerElement {
	@Property
	public is_app = this.app.standalone;

	public titlebar: GtTitleBar;
	public sidebar: GtSidebar;
	public view: GtView;

	@Inject
	@On({
		"reconnect": "Reconnecting",
		"connected": "Connected",
		"disconnected": "Disconnected",
		"closed": "Closed",
		"version-changed": "VersionChanged",
		"reset": "Reset"
	})
	private server: Server;

	// Inject roster service for early preloading
	@Inject
	private roster: RosterService;

	public disconnected: GtDialog;
	private dead: boolean = false;
	private cause: number = 0;
	private details: string = null;

	private ready() {
		/*this.titlebar = this.$.title;
		this.sidebar = this.$.side;
		this.view = this.$.view;
		this.disconnected = this.$.disconnected;*/
	}

	public showDisconnected(state: boolean) {
		if (this.dead) return;
		if (state) this.disconnected.show(true);
		else setTimeout(() => !this.dead && this.disconnected.hide(), 1000);
	}

	public showDead(cause: number, details: string = null) {
		if (this.dead) return;
		this.dead = true;
		this.cause = cause;
		this.details = details;
		if (!this.disconnected.shown)
			this.disconnected.show(true);
	}

	private Connected() {
		this.showDisconnected(false);
	}

	private Reconnecting() {
		this.showDisconnected(true);
	}

	private Disconnected() {
		this.showDead(1);
	}

	private VersionChanged() {
		this.showDead(2);
	}

	private Reset() {
		this.showDead(3);
	}

	private Closed(reason: string) {
		// broken
		//this.showDead(4, reason || "The WebSocket was closed");
	}

	private scrolled = false;

	private ViewScroll(e: Event) {
		let scrolled = this.$.view.scrollTop > 0;
		if (scrolled != this.scrolled) {
			this.scrolled = scrolled;
			let t: Element = this.$.title;
			if (scrolled) {
				t.classList.add("scrolled");
			} else {
				t.classList.remove("scrolled");
			}
		}
	}
}
