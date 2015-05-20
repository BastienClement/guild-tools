import { GtLoader } from "elements/gt-loader";
import { Queue } from "utils/queue";
import { Deferred } from "utils/deferred";
import { XHRText } from "utils/xhr";
import { Server } from "client/server";

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

		const files = [
			"gt-loader",
			"gt-progress"
		].map(e => `elements/${e}`);

		require(files, next);
		return deferred.promise;
	}
};

/**
 * Load the source of one .less file
 */
function loadLess(file: string): Promise<string> {
	return XHRText(`/assets/less/${file}.less`);
}

/**
 * Inject compiled CSS code into the document
 */
function injectCSS(code: string) {
	const style = document.createElement("style");
	style.type = "text/css";
	style.appendChild(document.createTextNode(code));
	document.head.appendChild(style);
}

/**
 * Mark the begining of a pipeline step
 */
let steps = new Map<string, Promise<void>>();
function beginStep(step: string) {
	steps.set(step, Deferred.delay(250));
	const el = <HTMLDivElement> document.querySelector(`#load-${step}`);
	el.classList.add("current");
}

/**
 * Mark the end of a pipeline step
 */
function endStep(step: string) {
	return steps.get(step).then(() => {
		const el = <HTMLDivElement> document.querySelector(`#load-${step}`);
		el.classList.remove("current");
		el.classList.add("done");
	});
}

function error(title: string, message: string, infos: string = "") {
	const error_frame = <HTMLDivElement> document.querySelector("#error");
	error_frame.querySelector(".title").textContent = title;
	error_frame.querySelector(".text").textContent = message;
	error_frame.querySelector(".infos").textContent = infos;
	error_frame.style.display = "block";
}

/**
 * Load and initialize Guild Tools
 */
function main() {
	Deferred.pipeline(loadLess("loading"), [
		// Loading initialization
		(c) => less.render(c),
		(r) => injectCSS(r.css),

		// Less
		() => Deferred.pipeline(beginStep("less"), [
			() => loadLess("guildtools"),
			(c) => less.render(c),
			(r) => injectCSS(r.css),
			() => endStep("less")
		]),

		// Polymer
		() => Deferred.pipeline(beginStep("polymer"), [
			() => PolymerLoader.start(),
			() => endStep("polymer")
		]),

		// Socket
		() => Deferred.pipeline(beginStep("socket"), [
			() => Server.connect(),
			() => endStep("socket")
		]),

		// Auth
		() => Deferred.pipeline(beginStep("auth"), [
			() => Server.socket.openChannel("$GuildTools", null),
			() => endStep("auth")
		])
	]).then(() => {

	}, (e) => {
		error("An error occured while loading GuildTools", e.message);
	});
}

export = main;
