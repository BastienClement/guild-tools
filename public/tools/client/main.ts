import { Component, Injector } from "utils/di";
import { Deferred } from "utils/deferred";
import { Server } from "client/server"
import { Channel } from "gtp3/channel";
import { Loader } from "client/loader";
import { GtLogin } from "elements/loading";

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
		public injector: Injector) { }
	
	/**
	 * Initialize the GuildTools application
	 */
	main(): void {
		const socket_endpoint = this.loader.fetch("/api/socket_url");

		const init_pipeline = Deferred.pipeline(socket_endpoint, [
			(url: string) => this.server.connect(url),
			() => this.loader.loadLess("/assets/less/guildtools.less"),
			() => new AuthenticationDriver(this).start(),
			() => this.spinner_enabled ? this.stopSpinner() : null
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
		
		const last_dot = <HTMLSpanElement> document.querySelector("#loader .spinner b:last-child");
		if (!last_dot) return Deferred.resolved(null);
		
		const trigger = new Deferred<void>();
		
		const listener = () => {
			trigger.resolve(null);
			last_dot.removeEventListener("animationiteration", listener);
			const dots = document.querySelectorAll("#loader .spinner b");
			for (let i = 0; i < dots.length; ++i) {
				(<HTMLSpanElement> dots[i]).style.animationIterationCount = "1";
			}
		};
		
		last_dot.addEventListener("animationiteration", listener);
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
		const auth = this.app.server.openChannel("$AUTH").then(channel => {
			this.channel = channel;
			return this.auth();
		}).then(success => {
			if (success) return;
			this.session = null;
			localStorage.removeItem(KEY_AUTH_SESSION);
			return this.login();	
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
		if (!this.session) return Deferred.resolved(false);
		return this.channel.request<boolean>("auth", this.session);
	}
	
	/**
	 * Perform the login request
	 */
	private login(error?: string): Promise<void> {
		if (error) console.error(error);
		return this.requestCredentials().then(credentials => {
			const [user, pass] = credentials;
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
	private requestCredentials(): Promise<[string, string]> {
		if (!this.gt_login) return this.constructForm().then(() => this.requestCredentials());
		return (this.gt_login.credentials = new Deferred<[string, string]>()).promise;
	}
	
	/**
	 * Create the login form interface
	 */
	private constructForm(): Promise<void> {
		return this.loader.loadElement(GtLogin).then(() => this.app.stopSpinner()).then(() => {
			this.gt_login = new GtLogin();
			document.body.classList.add("step-login");
			document.body.appendChild(this.gt_login);
		});
	}
}
