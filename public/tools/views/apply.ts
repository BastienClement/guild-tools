import { Element, Dependencies, PolymerElement, Inject, Property, Listener, On, PolymerModelEvent } from "elements/polymer";
import { View, TabsGenerator } from "elements/app";
import { GtBox, GtAlert } from "elements/box";
import { GtButton, GtCheckbox, GtLabel, GtTextarea } from "elements/widgets";
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
@Dependencies(GtBox, GtTimeago)
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
@Dependencies(GtBox, GtTimeago, GtMarkdown)
export class ApplyDetailsMessage extends PolymerElement {
	@Property public message: ApplyMessage;
}

///////////////////////////////////////////////////////////////////////////////
// <apply-details>

@Element("apply-details", "/assets/views/apply.html")
@Dependencies(GtBox, GtTimeago, ApplyDetailsChar, ApplyDetailsMessage, GtCheckbox, GtLabel, GtTextarea)
export class ApplyDetails extends PolymerElement {
	@Inject
	@On({
		"message-posted": "MessagePosted",
		"unread-updated": "UnreadUpdated"
	})
	private service: ApplyService;
	
	/**
	 * The application ID
	 */
	@Property({ observer: "ApplyChanged" })
	public apply: number;
	
	/**
	 * Apply meta-informations, loaded by a <meta> tag
	 * We need to declare it here to attach an observer
	 */
	@Property({ observer: "DataAvailable" })
	private data: Apply;
	
	/**
	 * Track which tab is currently activated
	 */
	private tab: number;
	
	/**
	 *  The edit form is open
	 */
	private editOpen: boolean;
	
	/**
	 * Edit form disabled during message sending
	 */
	private editorDisabled: boolean;
	
	/**
	 * The message body
	 */
	private messageBody: string;
	
	/**
	 * Message type, can be "private" or "public"
	 */
	private messageType: string;
	
	/**
	 * Generate a fake ApplyMessage for the preview function
	 * It will be continuously updated with the content of the
	 * edit box. 
	 */
	@Property({ computed: "messageBody messageType" })
	private get messagePreview(): ApplyMessage {
		defer(() => this.ScrollDown());
		return {
			id: 0,
			apply: this.apply,
			user: this.app.user.id,
			date: new Date().toISOString(),
			text: this.messageBody,
			secret: this.messageType == "private",
			system: false
		};
	};
	
	/**
	 * The discussion feed data
	 */
	private feed: ApplyMessage[];
	
	/**
	 * The apply form data
	 */
	private body: string;
	
	/**
	 * Timeout for sending the apply-seen message
	 * When application is changed before 2s, the set-seen message
	 * is not sent
	 */
	private seenTimeout: any;
	
	/**
	 * Called when the selected apply changes
	 * Reset every state that may have changed
	 */
	private async ApplyChanged() {
		this.tab = void 0;
		this.editOpen = false;
		this.editorDisabled = false;
		this.messageBody = "";
		this.messageType = "private";
		this.feed = [];
		clearTimeout(this.seenTimeout);
	}
	
	/**
	 * Tab handlers
	 */
	private ShowDiscussion() { this.tab = 1; this.editOpen = false }
	private ShowDetails() { this.tab = 2; this.editOpen = false }
	private ShowManage() { this.tab = 3; this.editOpen = false }
	
	/**
	 * When data is available, decide which tab to activate
	 * and load feed and body.
	 * Also define the unread flag clearing timeout
	 */
	private async DataAvailable() {
		if (this.tab === void 0) {
			//this.tab = this.data.have_posts ? 1 : 2;
			this.tab = 1;
		}
		
		if (!this.apply) return;
		[this.feed, this.body] = await Promise.atLeast(200, this.service.applyFeedBody(this.apply));
		
		clearTimeout(this.seenTimeout);
		if (this.service.unreadState(this.apply)) {
			this.seenTimeout = setTimeout(() => this.service.setSeen(this.apply), 2000);
		}
	}
	
	/**
	 * Scroll the discussion tab to the bottom
	 * Attempt to be as smart as possible to prevent automatic scrolling
	 * if user scrolled manually
	 */
	@throttled private async ScrollDown(force?: boolean) {
		let node = this.$.discussion.$.wrapper;
		let bottom = node.scrollTop + node.clientHeight + 200;
		if (force || node.scrollTop < 50 || bottom > node.scrollHeight) {
			node.scrollTop = node.scrollHeight;
		}
	}
	
	/**
	 * Catch message rendered event and trigger automatic scrolldown
	 * Also bind to messages images
	 */
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
	
	/**
	 * Open the new message editor
	 */
	private async OpenEdit() {
		this.editOpen = true;
		await Promise.delay(200);
		this.ScrollDown(true);
	}
	
	/**
	 * Send message to server
	 */
	private async SendMessage() {
		this.editorDisabled = true;
		let success = await Promise.atLeast(500, this.service.postMessage(this.apply, this.messageBody, this.messageType != "public"));
		if (success) {
			this.messageBody = "";
			this.messageType = "private";
			this.editOpen = false;
		}
		this.editorDisabled = false;
	}
	
	/**
	 * Close the new message editor
	 */
	private CloseEdit() {
		this.editOpen = false;
	}
	
	/**
	 * New message posted on a application feed
	 */
	private MessagePosted(message: ApplyMessage) {
		if (message.apply == this.apply) {
			this.push("feed", message);
		}
	}
	
	/**
	 * Catch update to unread state for an open application
	 * and cancel it
	 */
	private UnreadUpdated(apply: number, unread: boolean) {
		if (apply == this.apply && unread) {
			this.service.setSeen(apply);
		}
	}
	
	/**
	 * Generate the link to the user profile
	 */
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
	@On({ "apply-updated": "ApplyUpdated" })
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
	
	private async ApplyUpdated() {
		this.applys = await this.service.openApplysList();
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
