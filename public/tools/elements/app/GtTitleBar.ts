import {PolymerElement} from "../../polymer/PolymerElement";
import {Element, Property, Watch, Listener, Inject, On} from "../../polymer/Annotations";
import {GtButton} from "../widgets/GtButton";
import {ViewMetadata, Tab} from "../../client/router/View";
import {Router} from "../../client/router/Router";
import {Server} from "../../client/server/Server";
import {ChatService} from "../../services/chat/ChatService";

@Element({
	selector: "gt-title-bar",
	template: "/assets/imports/app.html",
	dependencies: [GtButton]
})
export class GtTitleBar extends PolymerElement {
	// Remove the title bar if not launched as an app
	private ready() {
		if (!this.app.standalone)
			this.$["window-controls"].remove();
	}

	// ========================================================================
	// Loading

	@Inject
	@Watch({ loading: "UpdateLoading" })
	private server: Server;

	@Property
	private loading: boolean;

	private UpdateLoading() {
		this.loading = this.server.loading;
	}

	// ========================================================================
	// Chat

	@Inject
	@On({
		"connected": "UpdateOnlineCount",
		"disconnected": "UpdateOnlineCount"
	})
	private chat: ChatService;

	@Property
	public online_users: number = 0;

	private UpdateOnlineCount() {
		this.debounce("update-online", () => this.online_users = this.chat.onlinesUsers.length);
	}

	// ========================================================================
	// Tabs

	@Inject
	@On({ "route-updated": "UpdateTabs" })
	private router: Router;

	private tabs: Tab[];

	private async UpdateTabs() {
		// Get tabs generator from view metadata
		let meta = Reflect.getMetadata<ViewMetadata>("view:meta", this.router.activeView);
		if (!meta) return;

		this.tabs = meta.tabs(this.router.activeView, this.router.activePath, this.app.user).filter(t => !t.hidden);
	}

	// ========================================================================
	// Panel

	@Property({ reflect: true })
	public panel: boolean = false;

	@Listener("logo.click")
	private OpenPanel() {
		if (!this.panel) {
			this.panel = true;
			(<any>this).style.zIndex = 20;
		}
	}

	@Listener("panel.click")
	private PanelClicked(ev: MouseEvent) {
		if (this.panel) {
			ev.stopImmediatePropagation();
			ev.preventDefault();
			this.ClosePanel();
		}
	}

	@Listener("panel.mouseleave")
	private ClosePanel() {
		if (this.panel) {
			this.panel = false;
			this.debounce("z-index-downgrade", () => { if (!this.panel) (<any>this).style.zIndex = 10; }, 300);
		}
	}

	private Reload() {
		document.location.reload();
	}

	private DownloadClient() { }

	private DevTools() { }

	private Logout() { }

	private Quit() {
		window.close();
	}
}
