import { Component, Constructor } from "utils/di";
import { Deferred } from "utils/deferred";
import { PolymerElement, PolymerMetadata } from "elements/polymer";

// Path to the polymer file
const POLYMER_PATH = "/assets/imports/polymer.html";

// Alias to the fetch function for type-safety purpose
const Fetch: (url: string) => Promise<FetchResponse> = (<any>window).fetch;

// Keep references to already imported HTML document
const imported_documents: Map<string, Promise<Document>> = new Map<any, any>();

// Keep references to already imported LESS stylesheet
const imported_less: Map<string, Promise<void>> = new Map<any, any>();

// Track polymer status
let polymer_loaded = false;

/**
 * The Response object returned by fetch()
 */
export interface FetchResponse {
	text(): Promise<string>;
}

/**
 * Interface of a <template> tag
 */
interface HTMLTemplateElement extends Element {
	content: Document;
}

/**
 * Resource loading service
 */
@Component
export class Loader {
	/**
	 * Previous requests cache
	 */
	private fetch_cache: Map<string, Promise<string>> = new Map<string, any>();
	
	/**
	 * Fetch a server-side resource as text
	 */
	fetch(url: string, cache: boolean = true): Promise<string> {
		// Check the cache for the resource
		if (this.fetch_cache.has(url)) return this.fetch_cache.get(url);
		
		// Cache and return helper
		const cache_and_return = (data: Promise<string>) => {
			if (cache) {
				this.fetch_cache.set(url, data);
				// Remove from cache if fetch failed
				data.then(null, () => this.fetch_cache.delete(url));
			}
			return data;
		};
		
		// If the Fetch API is available, use it
		if (Fetch) return cache_and_return(Fetch(url).then((res) => res.text()))
		
		// Fallback to XHR
		const defer = new Deferred<string>();
		const xhr = new XMLHttpRequest();
		
		xhr.open("GET", url, true);
		xhr.responseType = "text";
		
		xhr.onload = function() {
			if (this.status == 200) {
				defer.resolve(this.response);
			} else {
				defer.reject(new Error());
			}
		};
		
		xhr.onerror = (e) => defer.reject(e);
		
		xhr.send();
		return cache_and_return(defer.promise);
	}
	
	/**
	 * Import a LESS stylesheet
	 */
	loadLess(url: string): Promise<void> {
		let promise = imported_less.get(url);
		if (promise) return promise;
		
		promise = this.fetch(url).then(source => this.compileLess(source)).then(css => {
			const style = document.createElement("style");
			style.innerHTML = css;
			style.setAttribute("data-source", url);
			document.head.appendChild(style);
		});
		
		imported_less.set(url, promise);
		return promise;
	}
	
	/**
	 * Compile LESS source to CSS
	 */
	compileLess(source: string): Promise<string> {
		// Split the input file on every dynamic import
		const parts = source.split(/@import\s*\(dynamic\)\s*"([^"]*)";?/);
		
		// Fetch imports
		const dyn_imports: Promise<string>[] = [];
		for (let i = 1; i < parts.length; ++i) {
			if (i % 2 == 1) dyn_imports.push(this.fetch(parts[i]));
		}
		
		return Deferred.all(dyn_imports).then(dyn_source => {
			for (let i = 0; i < dyn_source.length; ++i) {
				parts[i * 2 + 1] = dyn_source[i];
			}
			return less.render(parts.join(""));
		}).then(output => output.css);
	}
	
	/**
	 * Perform an HTML import
	 */
	loadDocument(url: string): Promise<Document> {
		let promise = imported_documents.get(url);
		if (promise) return promise;
		
		const link = document.createElement("link");
		link.rel = "import";
		link.href = url;
		
		promise = Deferred.onload(link).then((el: any) => {
			const doc = el.import;
			if (!doc) throw new Error(`HTML import of ${url} failed`);
			return el.import
		});
		
		imported_documents.set(url, promise);
		document.head.appendChild(link);
		
		return promise;
	}
	
	/**
	 * Load and instantiate a Polymer element
	 */
	loadElement<T extends PolymerElement>(element: T): Promise<T> {
		// Ensure that Polymer is loaded
		if (!polymer_loaded) {
			polymer_loaded = true;
			Polymer = <any> { dom: "shadow" };
			return this.loadDocument(POLYMER_PATH).then(() => this.loadElement(element));
		}
		
		// Read Polymer metadata;
		const meta: PolymerMetadata = (<any> element).__polymer;
		
		// Check if the element was already loaded once
		if (meta.loaded) {
			return Deferred.resolved(element);
		} else {
			meta.loaded = true;
		}
		
		// Load dependencies of the element if any
		const load_dependencies = (meta.dependencies) ?
			Deferred.all(meta.dependencies.map(dep => this.loadElement(dep))) :
			Deferred.resolved(null);

		// Load the template file
		return load_dependencies.then(() => this.loadDocument(meta.template)).then(document => {
			const domModule = document.querySelector(`dom-module[id=${meta.selector}]`);
			if (!domModule) throw new Error(`no <dom-module> found for element <${meta.selector}> in file '${meta.template}'`);
			return meta.domModule = domModule;
		}).then((domModule) => {
			// Compile LESS
			const less_styles = domModule.querySelectorAll(`style[type="text/less"]`);
			if (less_styles.length < 1) return;
			
			const job = (i: number) => {
				const style = <HTMLStyleElement> less_styles[i];
				return this.compileLess(style.innerHTML).then(css => {
					style.type = "text/css";
					style.innerHTML = css;
				});
			};
			
			const jobs: Promise<void>[] = [];
			for (let i = 0; i < less_styles.length; ++i) {
				jobs[i] = job(i);
			}
			
			return Deferred.all(jobs);
		}).then(() => {
			// Polymer constructor	
			meta.constructor = Polymer(meta.proto);
			return element;
		});
	}
}
