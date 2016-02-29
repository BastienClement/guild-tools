import {Component, Injector} from "../utils/DI";
import {Server} from "./server/Server";
import {Router} from "./router/Router";
import {GtApp} from "../elements/app/GtApp";
import {Channel} from "../gtp3/Channel";
import {Loader} from "./loader/Loader";
import {User} from "./server/User";
import {routes} from "./router/routes"

// LocalStorage key storing user's session
const KEY_AUTH_SESSION = "auth.session";

/**
 * GuildTools
 */
@Component
export class Application {
	constructor(public server: Server,
	            public loader: Loader,
	            public router: Router,
	            public injector: Injector) { }

	/**
	 * The root Application Node
	 */
	public root: GtApp = null;

	/**
	 * App is running in standalone mode
	 */
	public standalone: boolean = APP;

	/**
	 * App is running in dev mode
	 */
	public dev: boolean = DEV;

	/**
	 * The current user
	 */
	public user: User = null;

	/**
	 * The master channel
	 */
	public master: Channel = null;

	/**
	 * Access the current view element
	 */
	public get view(): PolymerElement {
		return (<any>this.root.view).current;
	}

	/**
	 * Initialize the GuildTools application
	 */
	async main(): Promise<void> {
		let fast = localStorage.getItem("loading.fast") == "1";
		let socket_endpoint = this.loader.fetch("/api/socket_url");
		let body = document.body;

		// Automatically replace ws:// by wss://
		if (document.location.protocol == "https:") {
			socket_endpoint = socket_endpoint.then(url => url.replace(/^ws:/, "wss:"));
		}

		// Preload GtApp and views
		let app_promise = this.loader.loadElement(GtApp);
		this.router.loadRoutes(routes);

		// Connect to server and load main less file
		await Promise.all([
			this.loader.loadLess("/assets/less/guildtools.less"),
			socket_endpoint.then(ep => this.server.connect(ep))
		]);

		// Display the title bar if running in standalone
		let loader_title = document.getElementById("loader-titlebar");
		if (this.standalone) {
			loader_title.style.opacity = "1";
		} else {
			loader_title.remove();
		}

		// Open the authentication channel and attempt authentication
		try {
			this.user = null;
			let auth_channel = await this.server.openChannel("auth");
			this.user = await auth_channel.request<User>("auth", localStorage.getItem("auth.session"));
			auth_channel.close();
		} catch (e) {
			location.href = "/unauthorized";
			return;
		}

		if (!this.user) {
			location.href = (<any> window).sso_url();
			return;
		}

		// Start the authentication process
		if (!fast) await Promise.delay(500);

		// Ensure spinner is disabled
		await this.stopSpinner();

		// Hide the loading title bar
		if (GtApp) loader_title.style.opacity = "0";

		// Login transition
		body.classList.add("no-loader");
		body.classList.add("with-background");
		if (!fast) await Promise.delay(1100);
		body.classList.add("app-loader");

		// Open master channel
		let master_channel_promise = this.server.openChannel("master");

		// Await every required promises
		await Promise.all<any>([
			master_channel_promise.then(c => this.master = c),
			app_promise
		]);

		// Create the main container element
		body.appendChild(<any> (this.root = await this.loader.createElement(GtApp)));

		// Remove the loading title bar
		if (this.standalone) loader_title.remove();

		console.log("loading done");
		//setInterval(() => this.server.ping(), 10000);

		this.router.fallback = "/dashboard";
		this.router.update();
	}

	/**
	 * Track loading spinner state
	 */
	private spinner_enabled = true;

	/**
	 * Return a promise that will be resolved when the next iteration
	 * of the loadind spinner annimation is completed. Also stop the
	 * spinner annimation.
	 */
	stopSpinner(): Promise<void> {
		if (!this.spinner_enabled) return Promise.resolve(null);
		this.spinner_enabled = false;

		let last_dot = document.querySelector<HTMLSpanElement>("#loader .spinner b:last-child");
		let trigger = Promise.defer<void>();

		const listener = () => {
			trigger.resolve(null);
			last_dot.removeEventListener("animationiteration", listener);
			let dots = document.querySelectorAll<HTMLSpanElement>("#loader .spinner b");
			for (let i = 0; i < dots.length; ++i) {
				//noinspection TypeScriptUnresolvedVariable
				dots[i].style.animationIterationCount = "1";
			}
		};

		last_dot.addEventListener("animationiteration", listener);

		// Bypass the synchronization if loading.fast is set
		if (localStorage.getItem("loading.fast") == "1") return Promise.resolve();

		return trigger.promise.then(() => Promise.delay(500));
	}
}

