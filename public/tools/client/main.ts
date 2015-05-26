import { Deferred } from "utils/deferred";
import { XHRText } from "utils/xhr";
import { $ } from "utils/dom";
import { Channel } from "gtp3/channel";
import { polymer_load } from "elements/polymer";
import { GtLogin, GtScreen, GtLoader } from "elements/defs";
import { Server } from "client/server";
import { error, DialogActions } from "client/dialog";

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
			() => polymer_load(),
			() => end_step("polymer")
		]),

		// Socket
		() => Deferred.pipeline(begin_step("socket"), [
			() => Server.connect(),
			() => end_step("socket")
		]),

		// Auth
		() => Deferred.pipeline(begin_step("auth"), [
			() => Server.openChannel("auth").catch(e => Deferred.rejected(new Error("Unable to open the authentication channel."))),
			(c: Channel) => Deferred.finally(perform_auth(c), () => c.close()),
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
		console.error(e);
		
		Server.disconnect();
	});
}

export = main;
