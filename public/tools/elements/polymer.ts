import { Deferred } from "utils/deferred";
import { Queue } from "utils/queue";

/**
 * Polymer Loader code
 */
let load_queue = new Queue<[string, () => void]>();
let loaded_files = new Set<string>();

const PolymerLoader = {
	/**
	 * Register a new element to load
	 */
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
				link.onerror = (e) => deferred.reject(new Error(`Unable to load element template file '${file}.html'`));
				document.head.appendChild(link);
			}
		}

		require(["elements/defs"], next);
		return deferred.promise;
	}
};

/**
 * Dummy class to expose Polymer functions on elements
 */
export class PolymerElement {
	
}

/**
 * Declare a Polymer Element
 */
export function polymer(selector: string, bundle?: string) {
	return (target: Function) => PolymerLoader.register(selector, bundle, target);
}

/**
 * Declare a Polymer Property
 */
export function property(config: Object) {
	return (target: any, property: string) => {
		if (!target.properties) target.properties = {};
		target.properties[property] = config;
	}
}

/**
 * Bootstrap Polymer elements loading
 */
export function polymer_load(): Promise<void> {
	return PolymerLoader.start();
}
