import { Element, Dependencies, PolymerElement, Inject, Property, Listener, PolymerModelEvent } from "elements/polymer";
import { View, TabsGenerator } from "elements/app";
import { GtBox, GtAlert } from "elements/box";
import { GtButton } from "elements/widgets";
import { GtDialog } from "elements/dialog";
import { BnetThumb } from "elements/bnet";
import { GtTimeago } from "elements/timeago";
import { Server } from "client/server";
import { User, Char, Roster } from "services/roster";
import { ApplyService, Apply, ApplyMessage } from "services/apply";
import { join } from "utils/async";

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
// <apply-details-char>

@Element("apply-details-message", "/assets/views/apply.html")
@Dependencies(GtBox, BnetThumb, GtTimeago)
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
	@Property
	private details: boolean;
	private feed: ApplyMessage[];
	private form: any;
	
	// Called when the selected apply changes
	private async ApplyChanged() {
		this.details = void 0;
		this.feed = [];
		this.feed = await this.service.applyFeed(this.apply);
		console.log(this.feed);
	}
	
	// Tabs handlers
	private ShowDiscussion() { this.details = false; }
	private ShowDetails() { this.details = true; }
	
	// When data is available, decide which tab to activate
	private DataAvailable() {
		if (this.details === void 0) {
			this.details = !this.data.have_posts;
		}
	}
	
	// Generate the link to the user profile
	@Property({ computed: "data.user" })
	private get profileLink(): string {
		return "/profile/" + this.data.user;
	}
}

///////////////////////////////////////////////////////////////////////////////
// <gt-apply>

@View("apply", ApplyTabs)
@Element("gt-apply", "/assets/views/apply.html")
@Dependencies(GtBox, GtAlert, GtButton, GtDialog, ApplyListItem, ApplyDetails, Roster)
export class GtApply extends PolymerElement {
	@Inject
	private service: ApplyService;
	
	private applys: number[];
	
	@Property
	private selected: number = null;
	
	private async init() {
		this.applys = await this.service.openApplysList();
	}
	
	private SelectApply(ev: PolymerModelEvent<number>) {
		this.selected = ev.model.item;
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
