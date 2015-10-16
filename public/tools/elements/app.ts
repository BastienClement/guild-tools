import { Element, Property, Listener, Dependencies, Inject, On, Watch, Bind, PolymerElement, PolymerModelEvent, PolymerMetadata } from "elements/polymer";
import { GtButton } from "elements/widgets";
import { Router, ModuleTab } from "client/router";
import { Server } from "client/server";
import { Loader } from "client/loader";
import { Deferred } from "utils/deferred";
import { Chat } from "services/chat";

@Element("gt-title-bar", "/assets/imports/app.html")
@Dependencies(GtButton)
export class GtTitleBar extends PolymerElement {
	// Remove the title bar if not launched as an app
	private ready() {
		if (!APP) this.$["window-controls"].remove();
	}
	
	@Inject
	@On({ "update-latency": "UpdateLatency" })
	@Bind({ loading: "loading" })
	private server: Server;

	@Property
	public latency: string = "0ms";
	private latency_history: number[] = [];
	
	@Property
	private loading: boolean;

	// Update the latency indicator
	private UpdateLatency() {
		// Push the latency in the history array
		const history = this.latency_history;
		history.push(this.server.latency);
		if (history.length > 4) history.shift();

		// Sum and count of history values
		let acc = history.reduce((acc, l) => {
			acc.sum += l;
			acc.count++;
			return acc;
		}, { sum: 0, count: 0 });

		// Average
		const latency = acc.sum / acc.count;

		// Update the latency value
		this.latency = Math.floor(latency * 100) / 100 + "ms";
	}

	@Inject
	@On({
		"connected": "UpdateOnlineCount",
		"disconnected": "UpdateOnlineCount"
	})
	private chat: Chat;

	@Property
	public online_users: number = 0;
	
	private UpdateOnlineCount() {
		this.debounce("update-count", () => this.online_users = this.chat.onlinesUsers.length);
	}

	@Inject
	@Bind({ activeTabs: "tabs", activePath: "path" })
	private router: Router;

	private tabs: ModuleTab[];
	private path: string;

	private tabActive(tab: ModuleTab) {
		if (tab.pattern) {
			return tab.pattern.test(this.path);
		} else {
			return tab.link == this.path;
		}
	}

	private activateTab(e: PolymerModelEvent<ModuleTab>) {
		this.router.goto(e.model.item.link);
	}

	private tabVisible(tab: ModuleTab) {
		return !tab.visible || tab.visible(this.app.user);
	}
}

interface SidebarIcon {
	icon: string;
	key: string;
	link: string;
}

@Element("gt-sidebar", "/assets/imports/app.html")
export class GtSidebar extends PolymerElement {
	/**
	 * List of icons to display on the sidebar
	 */
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

	/**
	 * Reference to the application router to get the
	 * current main-view module
	 */
	@Inject
	@Bind({ activeModule: "module" })
	private router: Router;

	// Current active module
	private module: string;

	/**
	 * Icon click handler
	 */
	private IconClicked(e: PolymerModelEvent<{ link: string }>) {
		this.router.goto(e.model.item.link)
	}

	/**
	 * Check if two strings are equals
	 */
	private equals<T>(a: T, b: T) {
		return a == b;
	}
}

@Element("gt-view", "/assets/imports/app.html")
export class GtView extends PolymerElement {
	@Inject
	private loader: Loader;

	@Inject
	@Watch({ activeView: "update" })
	private router: Router;

	private layer: number = 0;
	private current: any = null;

	private update() {
		document.body.classList.add("app-loader");
		this.current = null;

		let views: Element[] = this.node.children;
		let tasks: Promise<any>[] = views.map(e => {
			return new Promise((res) => {
				e.classList.remove("active");
				e.addEventListener("transitionend", res);
			}).then(() => this.node.removeChild(e));
		});

		let view = this.router.activeView;
		if (!view) return;
		
		tasks.push(this.loader.loadElement(view));

		Promise.all(tasks).then(() => {
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
			let factory = document.createElement("div");
			let meta = Reflect.getMetadata<PolymerMetadata<any>>("polymer:meta", view);
			factory.innerHTML = `<${meta.selector}${arg_string.join()}></${meta.selector}>`;
			
			// Element has been constructed by the HTML parser
			let element = <any> factory.firstElementChild;

			element.style.zIndex = ++this.layer;

			this.current = element;
			this.node.appendChild(element);

			requestAnimationFrame(() => requestAnimationFrame(() => element.classList.add("active")));
		});
	}
}


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
