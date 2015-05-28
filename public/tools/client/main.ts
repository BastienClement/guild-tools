import { Deferred } from "utils/deferred";
import { Injector, Constructor } from "utils/di";
import { Channel } from "gtp3/channel";
import { error, DialogActions } from "client/dialog";
import { Server } from "core/server";

// ###############################################

const injector = new Injector();

// Lazy server object loading
let server: Server = null;
const lazy_server = () => Deferred.require<Constructor<Server>>("core/server", "Server").then(s => server = injector.get(s));

// Lazy polymer element loading
const lazy_polymer = () => Deferred.require<() => Promise<void>>("elements/polymer", "polymer_load").then(loader => loader())

/**
 * Components lazy-loading
 */
import { GtLogin as GtLogin_t, GtScreen as GtScreen_t } from "elements/defs";
let GtLogin: typeof GtLogin_t = null;
let GtScreen: typeof GtScreen_t = null;
const import_main_components = () => Deferred.parallel([
	Deferred.require<typeof GtLogin_t>("elements/defs", "GtLogin").then(impl => GtLogin = impl),
	Deferred.require<typeof GtScreen_t>("elements/defs", "GtScreen_t").then(impl => GtScreen = impl)
]);

// ###############################################

/**
 * Simple wrapper around Element#querySelector
 */
function $(selector: string, parent: Element | Document = document): Element {
	return parent.querySelector(selector);
}

/**
 * Load text data via XMLHttpRequest
 */
function xhr_text(url: string): Promise<string> {
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

	xhr.send();
	return deferred.promise;
}

// ###############################################

/**
 * Load the source of one .less file
 */
function load_less(file: string): Promise<string> {
	return xhr_text(`/assets/less/${file}.less`);
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

// ###############################################

/**
 * Mark the begining of a pipeline step
 */
let steps = new Map<string, Promise<void>>();
function begin_step(step: string) {
	steps.set(step, Deferred.delay(1));
	const el = <HTMLDivElement> $(`#load-${step}`);
	el.classList.add("current");
}

/**
 * Mark the end of a pipeline step
 */
function end_step(step: string) {
	return steps.get(step).then(() => {
		const el = <HTMLDivElement> $(`#load-${step}`);
		el.classList.remove("current");
		el.classList.add("done");
	});
}

// ###############################################

/**
 * Perform the authentification with the server
 */
const STORAGE_SESSION_KEY = "auth.session";
function perform_auth(chan: Channel): Promise<void> {
	let session: string = localStorage.getItem(STORAGE_SESSION_KEY);
	let gt_login: GtLogin_t = null;
	
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
		const creds = new Deferred<[string, string]>();
		
		if (!gt_login) {
			gt_login = new GtLogin();
			document.body.appendChild(gt_login);
		}
		
		gt_login.credentials = creds;
		console.log("credentials requested");
		
		creds.promise.then(() => gt_login.credentials = null);
		
		return creds.promise;
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

// ###############################################

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
 * Load and initialize Polymer components
 */
const init_polymer = () => Deferred.pipeline(begin_step("polymer"), [
	() => lazy_polymer(),
	() => import_main_components(),
	() => end_step("polymer")
]);

/**
 * Fetch the server API endpoint and connect
 */
const init_socket = () => Deferred.pipeline(begin_step("socket"), [
	() => lazy_server(),
	() => xhr_text("/api/socket_url"),
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

// ###############################################

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
		init_polymer,
		init_socket,
		init_auth
	]);

	init_pipeline.then(() => {
		// Loading successful
		document.body.appendChild(new GtScreen());
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
