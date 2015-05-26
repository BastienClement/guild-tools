import { GtLoader } from "elements/gt-loader";
import { Queue } from "utils/queue";
import { Deferred } from "utils/deferred";
import { XHRText } from "utils/xhr";
import { Server } from "client/server";
import { error, DialogActions } from "client/dialog";
import { $ } from "utils/dom";
import { Channel } from "gtp3/channel";
import { GtLogin, GtScreen } from "elements/defs";

let load_queue = new Queue<[string, () => void]>();
let loaded_files = new Set<string>();

/**
 * Polymer loader
 */
PolymerLoader = {
	register(selector: string, bundle: string, target: Function) {
		// Transpose instance variable on prototype
		target.call(target.prototype);
		target.prototype.is = selector;

		// The polymer constructor function
		let PolymerConstructor: any = null;

		// The placeholder during element loading
		const PolymerPlaceholder: any = function () {
			if (!PolymerConstructor) throw new Error("Polymer element is not yet ready");
			return PolymerConstructor.apply(Object.create(PolymerConstructor.prototype), arguments);
		};

		load_queue.enqueue([bundle || selector, () => {
			PolymerConstructor = Polymer(target.prototype);
		}]);
		
		return <any> PolymerPlaceholder;
	},

	/**
	 * Start the loading process
	 */
	start(): Promise<void> {
		const deferred = new Deferred<void>();

		// Async template loading loop
		function next() {
			// Everything has been loaded
			if (load_queue.empty()) {
				load_queue = null;
				loaded_files = null;
				deferred.resolve();
				return;
			}

			// Get next element
			let [file, callback] = load_queue.dequeue();

			// Load callback
			const load = () => {
				callback();
				next();
			};

			// Load the file if not already loaded
			if (loaded_files.has(file)) {
				load();
			} else {
				const link = document.createElement("link");
				link.rel = "import";
				link.href = `/assets/imports/${file}.html`;
				link.onload = load;
				document.head.appendChild(link);
			}
		}

		require("elements/defs", next);
		return deferred.promise;
	}
};

/**
 * Load the source of one .less file
 */
function load_less(file: string): Promise<string> {
	return XHRText(`/assets/less/${file}.less`);
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
let steps = new Map<string, Promise<void>>();
function begin_step(step: string) {
	steps.set(step, Deferred.delay(250));
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

/**
 * Perform the authentification with the server
 */
function perform_auth(chan: Channel): Promise<void> {
	let session: string = localStorage.getItem("auth.session");
	let gt_login: GtLogin = null;
	
	function attempt_auth(): Promise<boolean> {
		if (!session) return Deferred.resolved(false);
		return chan.request<boolean>("auth", session);
	}
	
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
	
	function perform_login(error?: string): Promise<void> {
		return request_credentials().then(creds => {
			return chan.request<string>("login", { user: creds[0], pass: creds[1] });
		}).then(sid => {
			session = sid;
			localStorage.setItem("auth.session", sid);
			return attempt_auth();
		}).then(success => {
			if (!success) throw new Error("Auth failed after a successful login");	
		}).catch(e => perform_login(e.message));
	}
	
	return attempt_auth().then(success => {
		if (success) {
			return null;
		} else {
			session = null;
			localStorage.removeItem("auth.session");
			return perform_login();
		}
	}).then(() => {
		if (gt_login) {
			document.body.removeChild(gt_login);
		}
	});
}

/**
 * Load and initialize Guild Tools
 */
function main() {
	Server.on("*", function() {
		console.log(arguments);
	});

	Deferred.pipeline(load_less("loading"), [
		// Loading initialization
		(c) => less.render(c),
		(r) => inject_css(r.css),

		// Less
		() => Deferred.pipeline(begin_step("less"), [
			() => load_less("guildtools"),
			(c) => less.render(c),
			(r) => inject_css(r.css),
			() => end_step("less")
		]),

		// Polymer
		() => Deferred.pipeline(begin_step("polymer"), [
			() => PolymerLoader.start(),
			() => end_step("polymer")
		]),

		// Socket
		() => Deferred.pipeline(begin_step("socket"), [
			() => Server.connect(),
			() => end_step("socket")
		]),

		// Auth
		() => Deferred.pipeline(begin_step("auth"), [
			() => Server.openChannel("auth"),
			(c: Channel) => perform_auth(c).then(() => c.close(), e => {
				c.close();
				throw e;
			}),
			() => end_step("auth")
		])
	]).then(() => {
		document.body.appendChild(new GtScreen());
	}, (e) => {
		const actions: DialogActions[] = [
			{ label: "Reload", action: () => location.reload() }
		];
		
		if (window) {
			actions.push({ label: "Quit", action: () => location.reload() })
		}
		
		error("An error occured while loading GuildTools", e.message, null, actions);
	});
}

export = main;
