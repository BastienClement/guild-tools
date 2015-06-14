import { Component, Injector } from "utils/di";
import { Deferred } from "utils/deferred";
import { Server } from "client/server"
import { Channel } from "gtp3/channel";
import { Loader } from "client/loader";
import { GtLogin } from "elements/loading";
import { GtApp } from "elements/app";

// LocalStorage key storing user's session
const KEY_AUTH_SESSION = "auth.session";

interface UserInformations {
	id: number;
	name: string;
	group: number;
	color: string;
}

/**
 * GuildTools
 */
@Component
export class Application {
    constructor(
        public server: Server,
		public loader: Loader,
		public injector: Injector) { }
	
	/**
	 * Information about the current user
	 */
	public user: UserInformations = null;
	
	/**
	 * The root Application Node
	 */
	public root: GtApp = null;
	
	/**
	 * Initialize the GuildTools application
	 */
	main(): void {
		const socket_endpoint = this.loader.fetch("/api/socket_url");
		const body = document.body;

		const init_pipeline = Deferred.pipeline(socket_endpoint, [
			(url: string) => this.server.connect(url),
			() => this.loader.loadLess("/assets/less/guildtools.less"),
			() => new AuthenticationDriver(this).start(),
			() => this.spinner_enabled ? this.stopSpinner() : null,
			() => {
				body.classList.add("no-loader");
				body.classList.add("with-background");
				return Deferred.delay(1100);
			},
			() => {
				body.classList.add("app-loader");
				return this.loader.loadElement(GtApp);
			},
			() => {
				body.appendChild(this.root = new GtApp());
			}
		]);
		
		init_pipeline.then(() => {
			console.log("Loading done");
		}, (e) => {
			console.error("Loading failed", e);
		});
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
	stopSpinner() {
		this.spinner_enabled = false;
		
		const last_dot = document.querySelector<HTMLSpanElement>("#loader .spinner b:last-child");
		if (!last_dot) return Deferred.resolved(null);
		
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
		if (localStorage.getItem("loading.fast") == "1") return null;
		
		return trigger.promise;
	}
}

/**
 * Authentication procedure driver
 */
class AuthenticationDriver {
	private session: string = localStorage.getItem(KEY_AUTH_SESSION);
	private channel: Channel;
	private gt_login: GtLogin;
	private loader: Loader = this.app.loader;
	
	constructor(public app: Application) {}
	
	/**
	 * Begin the authentication process
	 */
	start(): Promise<void> {
		const auth = this.app.server.openChannel("auth").then(channel => {
			this.channel = channel;
			return this.auth();
		}).then(success => {
			if (success) return;
			this.session = null;
			localStorage.removeItem(KEY_AUTH_SESSION);
			return this.login();
		}).then(() => {
			if (this.gt_login) {
				return this.gt_login.close();
			}
		});
		
		Deferred.finally(auth, () => {
			if (this.channel) this.channel.close();
			if (this.gt_login) document.body.removeChild(this.gt_login);
		});
		
		return auth;
	}
	
	/**
	 * Send the session token to the server and return if
	 * the authentication was successful
	 */
	private auth(): Promise<boolean> {
		if (!this.session) return Deferred.resolved(null);
		return this.channel.request<UserInformations>("auth", this.session).then(user => {
			this.app.user = user;
			return !!user;
		});
	}
	
	/**
	 * Perform the login request
	 */
	private login(error?: string): Promise<void> {
		if (error) console.error(error);
		let user: string;
		let pass: string;
		return this.requestCredentials(error).then(credentials => {
			[user, pass] = credentials;
			return Deferred.all([this.channel.request("prepare", user), Deferred.require("phpbb_hash"), Deferred.require("cryptojs")]);
		}).then((res: any[]) => {
			const [prepare, phpbb_hash, crypto] = res;
			pass = crypto.SHA1(phpbb_hash(pass, prepare.setting) + prepare.salt).toString();
			return this.channel.request<string>("login", { user: user, pass: pass });
		}).then(sid => {
			this.session = sid;
			localStorage.setItem(KEY_AUTH_SESSION, sid);
			return this.auth();
		}).then(success => {
			if (!success) throw new Error("Auth failed after a successful login");
		}).catch(e => this.login(e.message));
	}
	
	/**
	 * Request user credentials
	 */
	private requestCredentials(error?: string): Promise<[string, string]> {
		if (!this.gt_login) return this.constructForm().then(() => this.requestCredentials());
		this.gt_login.error = error;
		this.gt_login.autofocus();
		return (this.gt_login.credentials = new Deferred<[string, string]>()).promise;
	}
	
	/**
	 * Create the login form interface
	 */
	private constructForm(): Promise<void> {
		return this.loader.loadElement(GtLogin).then(() => this.app.stopSpinner()).then(() => {
			this.gt_login = new GtLogin();
			document.body.classList.add("with-background");
			document.body.classList.add("no-loader");
			document.body.appendChild(this.gt_login);
		});
	}
}
