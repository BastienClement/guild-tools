import {Component, Constructor} from "../../utils/DI";
import {PolymerCompiler} from "./Polymer";
import {PolymerElement} from "../../polymer/PolymerElement";
import {LessCompiler} from "./Less";
import {PolymerElementDeclaration} from "../../polymer/Element";

// The Response object returned by fetch()
interface FetchResponse {
	text(): Promise<string>;
}

// Alias to the fetch function for type-safety purpose
const fetch: (url: string) => Promise<FetchResponse> = (<any>window).fetch;

// Status of the Polymer lib
const enum PolymerLoadState {
	Unloaded,
	Loading,
	Loaded
}

// Path to the polymer file
const POLYMER_PATH = "/assets/imports/polymer.html";

/**
 * Resource loading service
 * Handle LESS compilation and Polymer sugars
 */
@Component
export class Loader {
	constructor(public polymer: PolymerCompiler, public less: LessCompiler) {}

	/** The cache of fetched resource */
	private fetch_cache = new Map<string, Promise<string>>();

	/**
	 * Fetches a remote resource as text.
	 * @param url           The resource's URL
	 * @param use_cache     Whether to use the cache or not
	 */
	public async fetch(url: string, use_cache: boolean = true): Promise<string> {
		// Dummy fetch
		if (url.lastIndexOf("@dummy") != -1) {
			return "";
		}

		// Check the cache for the resource
		if (this.fetch_cache.has(url) && use_cache) {
			return await this.fetch_cache.get(url);
		}

		// Fetch the resource
		let res = fetch(url).then(res => res.text());

		// Put in cache
		if (use_cache) {
			this.fetch_cache.set(url, res);
			res.then(null, () => this.fetch_cache.delete(url));
		}

		return await res;
	}

	/** List of loaded less stylesheets */
	private less_cache = new Map<string, Promise<void>>();

	/**
	 * Loads a LESS stylesheets and add it to the document head.
	 * @param url   The stylesheet URL
	 */
	public loadLess(url: string): Promise<void> {
		let promise = this.less_cache.get(url);
		if (promise) return promise;

		promise = (async() => {
			let source = await this.fetch(url);
			let css = await this.less.compile(source, this);

			let style = document.createElement("style");
			style.innerHTML = css;
			style.setAttribute("data-source", url);
			document.head.appendChild(style);
		})();

		this.less_cache.set(url, promise);
		return promise;
	}

	/** Keep references to already imported HTML documents */
	private document_cache = new Map<string, Promise<Document>>();

	/**
	 * Performs an HTML import.
	 * @param url   The document URL
	 * @returns     A promise that will be resolved with the imported document fragment
	 */
	public loadDocument(url: string): Promise<Document> {
		let promise = this.document_cache.get(url);
		if (promise) return promise;

		let link = document.createElement("link");
		link.rel = "import";
		link.href = url;

		promise = <any> Promise.onload(link).then((el: any) => {
			const doc = el.import;
			if (!doc) throw new Error(`HTML import of ${url} failed`);
			return el.import;
		});

		this.document_cache.set(url, promise);
		document.head.appendChild(link);

		return promise;
	}

	/** Keep references to already imported Polymer elements */
	private element_cache = new Set<Constructor<PolymerElement>>();

	/** Keep track of Polymer status */
	private polymer_loaded: PolymerLoadState = PolymerLoadState.Unloaded;

	/** Polymer loading sync point */
	private polymer_sync: Promise<void> = null;

	/** List of elements to load automatically when Polymer is ready */
	private polymer_autoloads = <Constructor<PolymerElement>[]> [];

	/**
	 * Loads a Polymer element.
	 * Once the promise returned is resolved, the element can be safely instantiated.
	 * @param ctor  The polymer element constructor
	 */
	public async loadElement<T extends PolymerElement>(ctor: Constructor<T>): Promise<Constructor<T>> {
		// Check if the element was already loaded once
		if (this.element_cache.has(ctor)) {
			return ctor;
		}

		// Ensure that Polymer is loaded
		if (this.polymer_loaded != PolymerLoadState.Loaded) {
			if (this.polymer_loaded == PolymerLoadState.Unloaded) {
				this.polymer_loaded = PolymerLoadState.Loading;

				let defer = Promise.defer<void>();
				this.polymer_sync = defer.promise;

				// Use Shadow DOM
				(<any>window).Polymer = { dom: "shadow" };

				// Load polymer
				await this.loadDocument(POLYMER_PATH);
				this.polymer_loaded = PolymerLoadState.Loaded;

				// Load auto-load elements
				await Promise.all(this.polymer_autoloads.map(a => this.loadElement(a)));
				this.polymer_autoloads = null;

				// Polymer is loaded
				defer.resolve();
			}

			// Wait on Polymer
			await this.polymer_sync;

			// Load the requested element
			return await this.loadElement(ctor);
		}

		this.element_cache.add(ctor);
		let decl = Reflect.getMetadata<PolymerElementDeclaration>("polymer:declaration", ctor);
		if (!decl) throw new Error(`No @Element declaration found for element ${ctor.name}`);

		// Load dependencies
		if (decl.dependencies) {
			await Promise.all(decl.dependencies.map(d => this.loadElement(d)));
		}

		// Load template
		if (decl.template) {
			let document = await this.loadDocument(decl.template);

			// Find the <dom-module> element
			let dom_module = document.querySelector(`dom-module[id=${decl.selector}]`);
			if (!dom_module) throw new Error(`no <dom-module> found for element <${decl.selector}> in file '${decl.template}'`);

			await this.polymer.compile(dom_module, this);
		}

		// Polymer is doing something stupid with document._currentScript, fake it.
		let doc: any = document;
		doc._currentScript = document.body;

		// Register
		let ret = <any> Polymer(ctor);

		delete doc._currentScript;
		return ret;
	}

	/**
	 * Loads and instantiates a Polymer element.
	 * @param ctor  The element constructor
	 */
	public async createElement<T extends PolymerElement>(ctor: Constructor<T>): Promise<T> {
		return new (await this.loadElement(ctor));
	}

	/**
	 * Register an element to auto load when polymer is loaded
	 * @param ctor
	 */
	public registerPolymerAutoload(ctor: Constructor<any>) {
		if (this.polymer_autoloads) {
			this.polymer_autoloads.push(ctor);
		} else {
			this.loadElement(ctor);
		}
	}
}
