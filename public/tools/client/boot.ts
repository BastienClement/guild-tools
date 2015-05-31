import { Deferred } from "utils/deferred";
import { Injector, Constructor } from "utils/di";
import { Application } from "client/main";

/**
 * The global reference to the Application instance
 */
declare var GuildTools: Application;

/**
 * This promise is resolved when loading.less is available
 */
declare const loading_less: Promise<string>;

/**
 * Perform the authentification with the server
 */
/*function perform_auth(chan: Channel): Promise<void> {
	let session: string = localStorage.getItem(STORAGE_SESSION_KEY);
	let gt_login: GtLogin = null;
	
	/**
	 * If a session ID is available, attempt to authenticate using this session
	 * /
	function attempt_auth(): Promise<boolean> {
		if (!session) return Deferred.resolved(false);
		return chan.request<boolean>("auth", session);
	}
	
	/**
	 * Request credentials to the user
	 * /
	function request_credentials(error?: string): Promise<[string, string]> {
		if (!gt_login) {
			return lazy_GtLogin(GtLogin => {
				gt_login = new GtLogin();
				document.body.appendChild(gt_login);
				return request_credentials(error);
			});
		}
		
		return (gt_login.credentials = new Deferred<[string, string]>()).promise;
	}
	
	/**
	 * Perform the full login procedure
	 * /
	function perform_login(error?: string): Promise<void> {
		return request_credentials().then(creds => {
			return chan.request<string>("login", { user: creds[0], pass: creds[1] });
		}).then(sid => {
			session = sid;
			localStorage.setItem(STORAGE_SESSION_KEY, sid);
			return attempt_auth();
		}).then(success => {
			if (!success) throw new Error("Auth failed after a successful login");	
		}).catch(e => perform_login(e.message));
	}
	
	/**
	 * Bootstrap the login loop
	 * /
	return attempt_auth().then(success => {
		if (success) {
			return null;
		} else {
			session = null;
			localStorage.removeItem(STORAGE_SESSION_KEY);
			return perform_login();
		}
	}).then(() => {
		if (gt_login) {
			document.body.removeChild(gt_login);
		}
	});
}*/

/**
 * Load and compile the GuildTools Less stylesheet
 */
/*const init_less = () => Deferred.pipeline(begin_step("less"), [
	() => load_less("guildtools"),
	(c) => less.render(c),
	(r) => inject_css(r.css),
	() => end_step("less")
]);*/

/**
 * Fetch the server API endpoint and connect
 */
/*const init_socket = () => Deferred.pipeline(begin_step("socket"), [
	() => lazy_server(),
	() => xhr("/api/socket_url"),
	(url: string) => server.connect(url),
	() => end_step("socket")
]);*/

/**
 * Open the authentication channel and perform the authentication
 */
/*const init_auth = () => Deferred.pipeline(begin_step("auth"), [
	() => server.openChannel("$AUTH").catch(e => Deferred.rejected(new Error("Unable to open the authentication channel."))),
	(c: Channel) => Deferred.finally(perform_auth(c), () => c.close()),
	() => end_step("auth")
]);*/

/**
 * Load and initialize Guild Tools
 */
function boot() {
	let DeferredLazy: typeof Deferred = null;
	loading_less.then((source: string) => {
		return less.render(source);
	}).then((res: LessRenderResults) => {
		const style = document.createElement("style");
		style.type = "text/css";
		style.appendChild(document.createTextNode(res.css));
		document.head.appendChild(style);
	}).then(() => new Promise((resolve, reject) => {
		require(["utils/deferred"], (mod: any) => resolve(DeferredLazy = mod.Deferred));
	})).then(() => {
		return DeferredLazy.require<Constructor<Injector>>("utils/di", "Injector").then(Injector => new Injector);
	}).then((injector: Injector) => {
		return DeferredLazy.require<Constructor<Application>>("client/main", "Application").then(Application => injector.get(Application));
	}).then((app: Application) => {
		GuildTools = app;
		app.main();
	});
}

export = boot;
