import { Deferred, LazyThen } from "utils/deferred";
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

export interface PolymerProxy extends Function {
	__polymer: PolymerMetadata;
	[key: string]: any;
}

export interface PolymerMetadata {
	selector: string;
	template: string;
	proto: any;
	dependencies: PolymerProxy[];
	loaded: boolean;
	domModule?: Element;
	constructor?: Function;
}

/**
 * Declare a Polymer Element
 */
export function Element(selector: string, template: string) {
	return (target: Function) => {
		// Transpose instance variables on prototype
		target.call(target.prototype);
		target.prototype.is = selector;
		
		// Reference to the element metadata object
		let meta: PolymerMetadata;

		// The placeholder during element loading
		const proxy: PolymerProxy = <any> function() {
			if (!meta.constructor) throw new Error("Polymer element is not yet loaded");
			return meta.constructor.apply(Object.create(meta.constructor.prototype), arguments);
		};
		
		// Create the metadata objet
		meta = proxy.__polymer = {
			selector: selector,
			template: template,
			proto: target.prototype,
			dependencies: (<any> target).__polymer_dependencies,
			loaded: false
		};
		
		// Transpose static members on the proxy function
		for (let key in target) {
			if (target.hasOwnProperty(key)) {
				proxy[key] = (<{ [key: string]: any; }><any> target)[key];
			}
		}
		
		return <any> proxy;
	};
}

/**
 * Delcare Polymer element dependencies
 */
export function Dependencies(...dependencies: Function[]) {
	return (target: Function) => {
		const meta = (<PolymerProxy> target).__polymer;
		if (!meta) (<any> target).__polymer_dependencies = dependencies
		else meta.dependencies = <PolymerProxy[]> dependencies;
	}
}

/**
 * Declare a Polymer Property
 */
export function Property(config: Object) {
	return (target: any, property: string) => {
		if (!target.properties) target.properties = {};
		target.properties[property] = config;
	}
}
