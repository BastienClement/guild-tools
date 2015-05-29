import { Deferred } from "utils/deferred";
import { Injector, Constructor } from "utils/di";
import { error, DialogActions } from "client/dialog";

import { Channel } from "gtp3/channel";

/**
 * The main application Injector
 */
const injector = new Injector();

/**
 * Lazy server component loading
 */
import { Server } from "core/server";
let server: Server = null;
const lazy_server = Deferred.lazy(() => {
	return Deferred.require<Constructor<Server>>("core/server", "Server").then(s => server = injector.get(s));
});

/**
 * Components lazy-loading
 */
import { GtLogin } from "elements/defs";
const lazy_GtLogin = Deferred.lazy(() => Deferred.require<typeof GtLogin>("elements/defs", "GtLogin"))

/**
 * Simple wrapper around Element#querySelector
 */
function $(selector: string, parent: Element | Document = document): Element {
	return parent.querySelector(selector);
}

/**
 * Load text data via XMLHttpRequest
 */
function xhr(url: string): Promise<string> {
	const deferred = new Deferred<string>();

	const xhr = new XMLHttpRequest();
	xhr.open("GET", url, true);
	xhr.responseType = "text";

	xhr.onload = function() {
		if (this.status == 200) {
			deferred.resolve(this.response);
		} else {
			deferred.reject(new Error());
		}
	};
	
	xhr.onerror = (e) => deferred.reject(e);

	xhr.send();
	return deferred.promise;
}

/**
 * Load the source of one .less file
 */
function load_less(file: string): Promise<string> {
	return xhr(`/assets/less/${file}.less`);
}

/**
 * Inject compiled CSS code into the document
 */
function inject_css(code: string) {
	const style = document.createElement("style");
	style.type = "text/css";
	style.appendChild(document.createTextNode(code));
	document.head.appendChild(style);
}

/**
 * Mark the begining of a pipeline step
 */
function begin_step(step: string) {
	const el = <HTMLDivElement> $(`#load-${step}`);
	el.classList.add("current");
}

/**
 * Mark the end of a pipeline step
 */
function end_step(step: string) {
	const el = <HTMLDivElement> $(`#load-${step}`);
	el.classList.remove("current");
	el.classList.add("done");
}

/**
 * Perform the authentification with the server
 */
const STORAGE_SESSION_KEY = "auth.session";
function perform_auth(chan: Channel): Promise<void> {
	let session: string = localStorage.getItem(STORAGE_SESSION_KEY);
	let gt_login: GtLogin = null;
	
	/**
	 * If a session ID is available, attempt to authenticate using this session
	 */
	function attempt_auth(): Promise<boolean> {
		if (!session) return Deferred.resolved(false);
		return chan.request<boolean>("auth", session);
	}
	
	/**
	 * Request credentials to the user
	 */
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
	 */
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
	 */
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
}

/**
 * Load and compile the GuildTools Less stylesheet
 */
const init_less = () => Deferred.pipeline(begin_step("less"), [
	() => load_less("guildtools"),
	(c) => less.render(c),
	(r) => inject_css(r.css),
	() => end_step("less")
]);

/**
 * Fetch the server API endpoint and connect
 */
const init_socket = () => Deferred.pipeline(begin_step("socket"), [
	() => lazy_server(),
	() => xhr("/api/socket_url"),
	(url: string) => server.connect(url),
	() => end_step("socket")
]);

/**
 * Open the authentication channel and perform the authentication
 */
const init_auth = () => Deferred.pipeline(begin_step("auth"), [
	() => server.openChannel("$AUTH").catch(e => Deferred.rejected(new Error("Unable to open the authentication channel."))),
	(c: Channel) => Deferred.finally(perform_auth(c), () => c.close()),
	() => end_step("auth")
]);

/**
 * Load and initialize Guild Tools
 */
function main() {
	const init_pipeline = Deferred.pipeline(load_less("loading"), [
		// Loading initialization
		(c) => less.render(c),
		(r) => inject_css(r.css),

		// Four steps initialization		
		init_less,
		init_socket,
		init_auth
	]);

	init_pipeline.then(() => {
		// Loading successful
		//document.body.appendChild(new GtScreen());
	}, (e) => {
		// Loading failed
		const actions: DialogActions[] = [
			{ label: "Reload", action: () => location.reload() }
		];
		
		if (window) {
			actions.push({ label: "Quit", action: () => location.reload() })
		}
		
		error("An error occured while loading GuildTools", e.message, null, actions);
		console.error(e);
		
		server.disconnect();
	});
}

export = main;
