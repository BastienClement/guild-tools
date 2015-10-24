import { Element, Dependencies, PolymerElement, Inject, Property, Listener, PolymerModelEvent } from "elements/polymer";
import { View, TabsGenerator } from "elements/app";
import { GtBox, GtAlert } from "elements/box";
import { GtButton } from "elements/widgets";
import { GtDialog } from "elements/dialog";
import { BnetThumb } from "elements/bnet";
import { GtTimeago } from "elements/timeago";
import { GtMarkdown } from "elements/markdown";
import { Server } from "client/server";
import { User, Char, Roster } from "services/roster";
import { ApplyService, Apply, ApplyMessage } from "services/apply";
import { join, throttled, defer } from "utils/async";

const ApplyTabs: TabsGenerator = (view, path, user) => [
	{ title: "Applys", link: "/apply", active: view == "views/apply/GtApply" },
	{ title: "Archives", link: "/apply/archives", active: view == "views/apply/GtApplyArchives" }
];

///////////////////////////////////////////////////////////////////////////////
// <apply-list-item>

@Element("apply-list-item", "/assets/views/apply.html")
@Dependencies(GtBox, BnetThumb, GtTimeago)
export class ApplyListItem extends PolymerElement {
	@Property public apply: number;
}

///////////////////////////////////////////////////////////////////////////////
// <apply-details-char>

@Element("apply-details-char", "/assets/views/apply.html")
@Dependencies(GtBox, BnetThumb, GtTimeago)
export class ApplyDetailsChar extends PolymerElement {
	@Property public id: number;
	
	private char: Char;
	
	@Property({ computed: "char.server" })
	private get serverName(): string {
		return this.char.server.replace("-", " ").replace(/\b([a-z])([a-z]+)/g, (all, first, tail) => {
			return (tail.length > 2) ? first.toUpperCase() + tail : first + tail;
		});
	}
	
	@Listener("click")
	private OnClick() {
		window.open(`http://eu.battle.net/wow/en/character/${this.char.server}/${this.char.name}/advanced`);
	}
}

///////////////////////////////////////////////////////////////////////////////
// <apply-details-message>

@Element("apply-details-message", "/assets/views/apply.html")
@Dependencies(GtBox, BnetThumb, GtTimeago, GtMarkdown)
export class ApplyDetailsMessage extends PolymerElement {
	@Property public message: ApplyMessage;
}

///////////////////////////////////////////////////////////////////////////////
// <apply-details>

@Element("apply-details", "/assets/views/apply.html")
@Dependencies(GtBox, GtTimeago, ApplyDetailsChar, ApplyDetailsMessage)
export class ApplyDetails extends PolymerElement {
	@Inject
	private service: ApplyService;
	
	// The apply ID4
	@Property({ observer: "ApplyChanged" })
	public apply: number;
	
	// Apply meta-informations, loaded by a <meta> tag
	@Property({ observer: "DataAvailable" })
	private data: Apply;
	
	// Indicate if the details tab is activated
	private details: boolean;
	
	// The discussion feed data
	private feed: ApplyMessage[];
	
	// The apply form data
	private body: string;
	
	// Timeout for sending the apply-seen message
	private seenTimeout: any;
	
	// Called when the selected apply changes
	private async ApplyChanged() {
		this.details = void 0;
		this.feed = [];
		clearTimeout(this.seenTimeout);
	}
	
	// Tabs handlers
	private ShowDiscussion() { this.details = false; }
	private ShowDetails() { this.details = true; }
	
	// When data is available, decide which tab to activate
	private async DataAvailable() {
		if (this.details === void 0) {
			//this.details = !this.data.have_posts;
			this.details = false;
		}
		
		if (!this.apply) return;
		[this.feed, this.body] = await Promise.atLeast(200, this.service.applyFeedBody(this.apply));
		
		clearTimeout(this.seenTimeout);
		if (this.service.unreadState(this.apply)) {
			this.seenTimeout = setTimeout(() => this.service.setSeen(this.apply), 2000);
		}
	}
	
	// Scroll the discussion tab to the bottom
	@throttled private async ScrollDown() {
		let node = this.$.discussion.$.wrapper;
		let bottom = node.scrollTop + node.clientHeight + 10;
		if (node.scrollTop < 10 || bottom > node.scrollHeight) {
			node.scrollTop = node.scrollHeight;
		}
	}
	
	@Listener("discussion.rendered")
	private async MessageRendered(ev: any) {
		// Get every images in the message
		let markdown = Polymer.dom(ev).rootTarget.$.markdown;
		let imgs = markdown.querySelectorAll("img");
		
		// Scroll when they are loaded
		for (let i = 0; i < imgs.length; i++) {
			let img: HTMLImageElement = imgs[i];
			Promise.onload(img).finally(() => this.ScrollDown());
		}
		
		// Scroll anyway at the end of the microtask
		await microtask;
		this.ScrollDown();
	}
	
	// Generate the link to the user profile
	@Property({ computed: "data.user" })
	private get profileLink(): string {
		return "/profile/" + this.data.user;
	}
}

///////////////////////////////////////////////////////////////////////////////
// <gt-apply>

@View("apply", ApplyTabs, true)
@Element("gt-apply", "/assets/views/apply.html")
@Dependencies(GtBox, GtAlert, GtButton, GtDialog, ApplyListItem, ApplyDetails, Roster)
export class GtApply extends PolymerElement {
	@Inject
	private service: ApplyService;
	
	private applys: number[];
	
	@Property({ observer: "ApplySelected" })
	private selected: number = null;
	
	private async init() {
		let selected = this.selected;
		this.selected = void 0;
		this.applys = await this.service.openApplysList();
		this.selected = selected;
	}
	
	private ApplySelected() {
		if (!this.applys) return;
		if (!this.applys.some(a => a === this.selected)) {
			this.selected = void 0;
		}
	}
	
	private SelectApply(ev: PolymerModelEvent<number>) {
		this.app.router.goto(`/apply/${ev.model.item}`);
	}
}

///////////////////////////////////////////////////////////////////////////////
// <gt-apply-redirect>

@View("apply", () => [{ title: "Apply", link: "/apply", active: true }])
@Element("gt-apply-redirect", "/assets/views/apply.html")
@Dependencies(GtButton)
export class GtApplyRedirect extends PolymerElement {
	@Listener("apply.click")
	private ApplyNow() { document.location.href = "http://www.fs-guild.net/tools/#/apply"; }
}
