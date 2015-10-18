import { Component, Injector } from "utils/di";
import { Deferred } from "utils/deferred";
import { Server, UserInformations } from "client/server";
import { Router } from "client/router";
import { Channel } from "gtp3/channel";
import { Loader } from "client/loader";
import { GtLogin } from "elements/loading";
import { GtApp } from "elements/app";
import { User } from "services/roster";

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
	 * The current user
	 */
	public user: User = null;

	/**
	 * The master channel
	 */
	public master: Channel = null;

	/**
	 * Initialize the GuildTools application
	 */
	async main(): Promise<void> {
		let fast = localStorage.getItem("loading.fast") == "1";
		let socket_endpoint = this.loader.fetch("/api/socket_url");
		let body = document.body;
		
		// Connect to server and load main less file        
		await Promise.all([
			this.loader.loadLess("/assets/less/guildtools.less"),
			socket_endpoint.then(ep => this.server.connect(ep))
		]);
		
		// Preload GtApp and views
		let app_views_promise = Promise.all<any>([
			this.loader.loadElement(GtApp),
			this.router.loadViews("views/load")
		]);
		
		// Display the title bar
		let loader_title = document.getElementById("loader-titlebar");
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
		let master_channel_promise = this.server.openChannel("master");
		
		// Await every required promises
		await Promise.all<any>([
			master_channel_promise.then(c => this.master = c),
			app_views_promise
		]);
		
		// Create the main container element
		body.appendChild(this.root = new GtApp());
		
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

		let last_dot = document.querySelector<HTMLSpanElement>("#loader .spinner b:last-child");
		let trigger = new Deferred<void>();

		const listener = () => {
			trigger.resolve(null);
			last_dot.removeEventListener("animationiteration", listener);
			let dots = document.querySelectorAll<HTMLSpanElement>("#loader .spinner b");
			for (let i = 0; i < dots.length; ++i) {
				dots[i].style.animationIterationCount = "1";
			}
		};

		last_dot.addEventListener("animationiteration", listener);

		// Bypass the synchronization if loading.fast is set
		if (localStorage.getItem("loading.fast") == "1") return Promise.resolve();

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
	public async start(): Promise<void> {
		this.channel = await this.app.server.openChannel("auth");
		let session = localStorage.getItem(KEY_AUTH_SESSION);
		
		// Authentication loop
		while (true) {
			// Check session if available
			if (session) {
				if (await this.auth(session)) {
					document.cookie = `gt_session=${session};path=/;max-age=604800`;
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
		let user = await this.channel.request<UserInformations>("auth", session);
		this.app.user = user;
		return !!user;
	}

	/**
	 * Perform the login request
	 */
	private async login(): Promise<string> {
		let error: string = null
		while (true) {
			let [user, raw_pass] = await this.requestCredentials(error);

			type PrepareData = { setting: string, salt: string };
			type PhpBBHash = (pass: string, setting: string) => string;
			type CryptoJS = { SHA1: (str: string) => Object };
			
			let [prepare, phpbb_hash, crypto] = <[PrepareData, PhpBBHash, CryptoJS]> await Promise.all([
				this.channel.request("prepare", user),
				Deferred.require("phpbb_hash"),
				Deferred.require("cryptojs")
			]);

			let pass = crypto.SHA1(phpbb_hash(raw_pass, prepare.setting) + prepare.salt).toString();

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
