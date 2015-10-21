import { Element, Property, Listener, Dependencies, Inject, On, Watch, Bind,
	PolymerElement, PolymerModelEvent, PolymerMetadata, PolymerConstructor } from "elements/polymer";
import { GtButton } from "elements/widgets";
import { Router } from "client/router";
import { Server } from "client/server";
import { Loader } from "client/loader";
import { Deferred } from "utils/deferred";
import { Chat } from "services/chat";
import { User } from "services/roster";

// Force loading of data providers elements
import "elements/data";

// Views loader
type ViewPromise = Promise<PolymerConstructor<any>>;
const views_cache = new Map<string, ViewPromise>();

function load_view(key: string) {
	if (views_cache.has(key)) {
		return views_cache.get(key);
	}
	
	let [, unit, name] = key.match(/^(.+)\/([^\/]+)$/);
	let promise: ViewPromise = Deferred.require(unit, name);
	
	promise.catch(e => views_cache.delete(key));
	
	views_cache.set(key, promise);
	return promise;
}

// Dummy factory div to construct views instances
const factory = document.createElement("div");

// Tab
export interface Tab {
	title: string;
	link: string;
	active: boolean;
}

// Function generating current tabs list
export type TabsGenerator = (view: string, path: string, user: User) => Tab[];

// Metadata for views
export interface ViewMetadata {
	module: string;
	tabs: TabsGenerator;
}

// The @View annoation
export function View(module: string, tabs: TabsGenerator) {
	return <T extends PolymerElement>(target: PolymerConstructor<T>) => {
		Reflect.defineMetadata("view:meta", { module, tabs }, target);
	};
}

///////////////////////////////////////////////////////////////////////////////
// <gt-title-bar>

@Element("gt-title-bar", "/assets/imports/app.html")
@Dependencies(GtButton)
export class GtTitleBar extends PolymerElement {
	// Remove the title bar if not launched as an app
	private ready() {
		if (!this.app.standalone)
			this.$["window-controls"].remove();
	}
	
	// ========================================================================
	// Loading

	@Inject
	@Bind({ loading: "loading" })
	private server: Server;

	@Property
	private loading: boolean;

	// ========================================================================
	// Chat

	@Inject
	@On({
		"connected": "UpdateOnlineCount",
		"disconnected": "UpdateOnlineCount"
	})
	private chat: Chat;

	@Property
	public online_users: number = 0;

	private UpdateOnlineCount() {
		this.debounce("update-online", () => this.online_users = this.chat.onlinesUsers.length);
	}
	
	// ========================================================================
	// Tabs

	@Inject
	@Bind({ activePath: "path" })
	@Watch({ activeView: "UpdateTabs" })
	private router: Router;

	private path: string;
	private tabs: Tab[];

	private async UpdateTabs() {
		// Capture the current active path to handle race-conditions
		let path = this.path;

		// Load the current view
		let view = await load_view(this.router.activeView);
		if (path != this.path) return;

		// Get tabs generator from view metadata	
		let meta = Reflect.getMetadata<ViewMetadata>("view:meta", view);
		if (!meta) return;
		
		this.tabs = meta.tabs(this.router.activeView, path, this.app.user);
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

	@Listener("panel.mouseleave")
	private ClosePanel(ev: MouseEvent) {
		if (this.panel) {
			this.panel = false;
			this.debounce("z-index-downgrade", () => { if (!this.panel) (<any>this).style.zIndex = 10; }, 300);
		}
	}

	private Reload() { document.location.reload(); }
	private DownloadClient() { }
	private DevTools() { }
	private Logout() { }
	private Quit() { window.close(); }
}

///////////////////////////////////////////////////////////////////////////////
// <gt-sidebar>

// Sidebar module icon
interface SidebarIcon {
	icon: string;
	key: string;
	link: string;
}

@Element("gt-sidebar", "/assets/imports/app.html")
export class GtSidebar extends PolymerElement {
	@Property
	private icons: SidebarIcon[] = [
		{ icon: "widgets", key: "dashboard", link: "/dashboard" },
		{ icon: "account_circle", key: "profile", link: "/profile" },
		//{ icon: "mail", key: "messages", link: "/messages" },
		{ icon: "today", key: "calendar", link: "/calendar" },
		{ icon: "group_work", key: "roster", link: "/roster" },
		//{ icon: "forum", key: "forum", link: "/forum" },
		{ icon: "assignment_ind", key: "apply", link: "/apply" },
		//{ icon: "ondemand_video", key: "streams", link: "/streams" },
		//{ icon: "brush", key: "whiteboard", link: "/whiteboard" },
		//{ icon: "backup", key: "drive", link: "/drive" }
	];
	
	@Inject
	@Bind({ activePath: "path" })
	@Watch({ activeView: "UpdateView" })
	private router: Router;

	// Current path
	private path: string;
    
	// Current active module
	private module: string;

	private async UpdateView() {
		// Capture the current active path to handle race-conditions
		let path = this.path;

		// Load the current view
		let view = await load_view(this.router.activeView);
		if (path != this.path) return;

		// Get module name from view metadata	
		let meta = Reflect.getMetadata<ViewMetadata>("view:meta", view);
		this.module = meta ? meta.module : null;
	}
    
	// Click handler for icons
	private IconClicked(e: PolymerModelEvent<{ link: string }>) {
		this.router.goto(e.model.item.link)
	}
}

///////////////////////////////////////////////////////////////////////////////
// <gt-view>

@Element("gt-view", "/assets/imports/app.html")
export class GtView extends PolymerElement {
	@Inject
	private loader: Loader;

	@Inject
	@Watch({ activeView: "update" })
	private router: Router;

	private layer: number = 0;
	private current: any = null;

	private async update() {
		document.body.classList.add("app-loader");
		this.current = null;

		let views: Element[] = this.node.children;
		let tasks: Promise<any>[] = views.map(e => {
			return new Promise((res) => {
				e.classList.remove("active");
				e.addEventListener("transitionend", res);
			}).then(() => this.node.removeChild(e));
		});

		let view_key = this.router.activeView;
		if (!view_key) return;
		this.router.lock(true);

		let view = await load_view(view_key);
		tasks.push(this.loader.loadElement(view));

		await Promise.all(tasks);
		document.body.classList.remove("app-loader");

		let args = this.router.activeArguments;
		let arg_string: string[] = [];

		for (let key in args) {
			if (args[key] != void 0) {
				let arg_value = args[key].replace(/./g, (s) => `&#${s.charCodeAt(0)};`);
				arg_string.push(` ${key}='${arg_value}'`);
			}
		}
		

		// Use a crazy HTML generation system to create the element since we need to have
		// router-provided attributes defined before the createdCallback() method is called
		let meta = Reflect.getMetadata<PolymerMetadata<any>>("polymer:meta", view);
		factory.innerHTML = `<${meta.selector}${arg_string.join()}></${meta.selector}>`;

		// Element has been constructed by the HTML parser
		let element = <any> factory.firstElementChild;

		element.style.zIndex = ++this.layer;

		this.current = element;
		this.node.appendChild(element);

		requestAnimationFrame(() => requestAnimationFrame(() => element.classList.add("active")));
		this.router.lock(false);
	}
}

///////////////////////////////////////////////////////////////////////////////
// <gt-app>

@Element("gt-app", "/assets/imports/app.html")
@Dependencies(GtTitleBar, GtSidebar, GtView)
export class GtApp extends PolymerElement {
	@Property
	public is_app: boolean = APP;

	public titlebar: GtTitleBar;
	public sidebar: GtSidebar;
	public view: GtView;

	private ready() {
		this.titlebar = this.$.title;
		this.sidebar = this.$.side;
		this.view = this.$.view;
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
