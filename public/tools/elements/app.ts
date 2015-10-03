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
	private init() {
		if (!APP) this.$["window-controls"].remove();
	}
	
	@Inject
	@On({
		"update-latency": "updateLatency"
	})
	private server: Server;

	@Property({ value: "0ms" })
	public latency: string;
	private latency_history: number[] = [];

	// Update the latency indicator
	private updateLatency() {
		// Push the latency in the history array
		const history = this.latency_history;
		history.push(this.server.latency);
		if (history.length > 4) history.shift();

		// Sum and count of history values
		const acc = history.reduce((acc, l) => {
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
		"connected": "updateOnlineCount",
		"disconnected": "updateOnlineCount"
	})
	private chat: Chat;

	@Property({ value: 0 })
	public online_users: number;
	
	private updateOnlineCount() {
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
	@Property({ type: Array })
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
	private "icon-click"(e: PolymerModelEvent<{ link: string }>) {
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

		const views: Element[] = this.node.children;
		const tasks: Promise<any>[] = views.map(e => {
			return new Promise((res) => {
				e.classList.remove("active");
				e.addEventListener("transitionend", res);
			}).then(() => this.node.removeChild(e));
		});

		const view = this.router.activeView;
		if (!view) return;
		tasks.push(this.loader.loadElement(view));

		Deferred.all(tasks).then(() => {
			document.body.classList.remove("app-loader");

			const meta = Reflect.getMetadata<PolymerMetadata<any>>("polymer:meta", view);
			const args = this.router.activeArguments;
			const element: any = document.createElement(meta.selector);

			for (let key in args) {
				let arg: any = args[key];
				if (arg !== void 0) {
					const type = Reflect.getMetadata<Function>("design:type", meta.proto, key);
					if (type == Boolean || type == Number || type == String) arg = type.call(null, arg);
				}
				element[key] = arg;
			}

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
	
	public titlebar: GtTitleBar = this.$.title;
	public sidebar: GtSidebar = this.$.side;
	public view: GtView = this.$.view;

	private scrolled = false;
	private "view-scroll"(e: Event) {
		const scrolled = this.$.view.scrollTop > 0;
		if (scrolled != this.scrolled) {
			this.scrolled = scrolled;
			const t = <Element> this.$.title;
			if (scrolled) {
				t.classList.add("scrolled");
			} else {
				t.classList.remove("scrolled");
			}
		}
	}
}
