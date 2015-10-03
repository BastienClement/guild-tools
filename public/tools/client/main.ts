import { Component, Injector } from "utils/di";
import { Deferred } from "utils/deferred";
import { Server, UserInformations } from "client/server";
import { Router } from "client/router";
import { Channel } from "gtp3/channel";
import { Loader } from "client/loader";
import { GtLogin } from "elements/loading";
import { GtApp } from "elements/app";

// LocalStorage key storing user's session
const KEY_AUTH_SESSION = "auth.session";

/**
 * GuildTools
 */
@Component
export class Application {
    constructor(
        public server: Server,
		public loader: Loader,
		public router: Router,
		public injector: Injector) { }

	/**
	 * The root Application Node
	 */
	public root: GtApp = null;

	/**
	 * The master channel
	 */
	public masterChannel: Channel = null;

	/**
	 * Initialize the GuildTools application
	 */
	async main(): Promise<void> {
		const fast = localStorage.getItem("loading.fast") == "1";
		const socket_endpoint = this.loader.fetch("/api/socket_url");
		const body = document.body;

		// Connect to server and load main less file        
		await Promise.all([
			this.loader.loadLess("/assets/less/guildtools.less"),
			this.server.connect(await socket_endpoint)
		]);
		
		// Display the title bar
		const loader_title = document.getElementById("loader-titlebar");
		if (APP) {
			loader_title.style.opacity = "1";
		} else {
			loader_title.remove();
		}
		
		// Start the authentication process
		if (!fast) await Deferred.delay(500);
		await new AuthenticationDriver(this).start();
		
		// Ensure spinner is disabled
		await this.stopSpinner();
		
		// Hide the loading title bar
		if (GtApp) loader_title.style.opacity = "0";
		
		// Login transition
		body.classList.add("no-loader");
		body.classList.add("with-background");
		if (!fast) await Deferred.delay(1100);
		body.classList.add("app-loader");
		
		// Open master channel
		this.masterChannel = await this.server.openChannel("master");
		
		// Load the main container element
		await this.loader.loadElement(GtApp);
		body.appendChild(this.root = new GtApp());
		
		await this.router.loadViews("views/defs")
		
		// Remove the loading title bar
		if (GtApp) loader_title.remove();
		
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

		const last_dot = document.querySelector<HTMLSpanElement>("#loader .spinner b:last-child");
		const trigger = new Deferred<void>();

		const listener = () => {
			trigger.resolve(null);
			last_dot.removeEventListener("animationiteration", listener);
			const dots = document.querySelectorAll<HTMLSpanElement>("#loader .spinner b");
			for (let i = 0; i < dots.length; ++i) {
				dots[i].style.animationIterationCount = "1";
			}
		};

		last_dot.addEventListener("animationiteration", listener);

		// Bypass the synchronization if loading.fast is set
		if (localStorage.getItem("loading.fast") == "1") return Promise.resolve(null);

		return trigger.promise.then(() => Deferred.delay(500));
	}
}

/**
 * Authentication procedure driver
 */
class AuthenticationDriver {
	private loader: Loader = this.app.loader;
	private channel: Channel;
	private gt_login: GtLogin;

	constructor(public app: Application) {}

	/**
	 * Begin the authentication process
	 */
	async start(): Promise<void> {
		this.channel = await this.app.server.openChannel("auth");
		let session = localStorage.getItem(KEY_AUTH_SESSION);
		
		// Authentication loop
		while (true) {
			// Check session if available
			if (session) {
				if (await this.auth(session)) {
					localStorage.setItem(KEY_AUTH_SESSION, session);
					break;
				} else {
					session = null;
				}    
			}
			
			session = await this.login();
		}
		
		this.channel.close();
		if (this.gt_login) {
			await this.gt_login.close();
			document.body.removeChild(this.gt_login);
		}
	}

	/**
	 * Send the session token to the server and return if
	 * the authentication was successful
	 */
	private async auth(session: string): Promise<boolean> {
		const user = await this.channel.request<UserInformations>("auth", session);
		this.app.server.user = user;
		return !!user;
	}

	/**
	 * Perform the login request
	 */
	private async login(): Promise<string> {
		let error: string = null
		while (true) {
			const [user, raw_pass] = await this.requestCredentials(error);

			const [prepare, phpbb_hash, crypto] = await Deferred.all<any>([
				this.channel.request("prepare", user),
				Deferred.require("phpbb_hash"),
				Deferred.require("cryptojs")
			]);

			const pass = crypto.SHA1(phpbb_hash(raw_pass, prepare.setting) + prepare.salt).toString();

			try {
				return await this.channel.request<string>("login", { user, pass });
			} catch (e) {
				error = e.message;
			}
		}    
	}

	/**
	 * Request user credentials
	 */
	private async requestCredentials(error: string): Promise<[string, string]> {
		if (!this.gt_login) await this.constructForm();
		this.gt_login.error = error;
		this.gt_login.autofocus();
		return (this.gt_login.credentials = new Deferred<[string, string]>()).promise;
	}

	/**
	 * Create the login form interface
	 */
	private async constructForm(): Promise<void> {
		await this.loader.loadElement(GtLogin);
		await this.app.stopSpinner();
		
		this.gt_login = new GtLogin();
		document.body.classList.add("with-background");
		document.body.classList.add("no-loader");
		document.body.appendChild(this.gt_login);
	}
}
