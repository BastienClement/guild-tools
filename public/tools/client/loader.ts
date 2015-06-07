import { Component, Constructor } from "utils/di";
import { Deferred } from "utils/deferred";
import { PolymerElement, PolymerConstructor, PolymerMetadata, apply_polymer_fns } from "elements/polymer";

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
	 * Handle @import (dynamic) statements
	 */
	private lessImportDynamics(source: string): Promise<string> {
		// Split the input file on every dynamic import
		const parts = source.split(/@import\s*\(dynamic\)\s*"([^"]*)";?/);
		
		// No import fournd
		if (parts.length == 1) return Deferred.resolved(source);
		
		// Fetch imports
		const dyn_imports: Promise<string>[] = [];
		for (let i = 1; i < parts.length; ++i) {
			if (i % 2 == 1) dyn_imports.push(this.fetch(parts[i]));
		}
		
		// Combine
		return Deferred.all(dyn_imports).then(dyn_source => {
			for (let i = 0; i < dyn_source.length; ++i) {
				parts[i * 2 + 1] = dyn_source[i];
			}
			
			// Recursive handling of deep @import (dynamic)
			return this.lessImportDynamics(parts.join(""));
		});
	}
	
	/**
	 * Compile LESS source to CSS
	 */
	compileLess(source: string): Promise<string> {
		// Prepend the 
		source = `
			@import (dynamic) "/assets/less/lib.less";
			${source}
		`;
		
		return this.lessImportDynamics(source).then(source => less.render(source)).then(output => output.css);
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
	loadElement<T extends PolymerElement>(element: PolymerConstructor<T>): Promise<PolymerConstructor<T>> {
		// Ensure that Polymer is loaded
		if (!polymer_loaded) {
			polymer_loaded = true;
			if (localStorage.getItem("polymer.useShadowDOM") == "1") Polymer = <any> { dom: "shadow" };
			return this.loadDocument(POLYMER_PATH).then(() => this.loadElement(element));
		} else if (!Polymer.is) {
			apply_polymer_fns();
		}
		
		// Read Polymer metadata
		const meta = element.__polymer;
		
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
			const domModule = document.querySelector<HTMLElement>(`dom-module[id=${meta.selector}]`);
			if (!domModule) throw new Error(`no <dom-module> found for element <${meta.selector}> in file '${meta.template}'`);
			return meta.domModule = domModule;
		}).then((domModule) => {
			// Compile LESS
			const less_styles = <NodeListOf<HTMLStyleElement>> domModule.querySelectorAll(`style[type="text/less"]`);
			if (less_styles.length < 1) return;

			const job = (i: number) => {
				const style = less_styles[i];
				return this.compileLess(style.innerHTML).then(css => {
					const new_style = document.createElement("style");
					new_style.innerHTML = css;
					
					style.parentNode.insertBefore(new_style, style);
					style.parentNode.removeChild(style);
				});
			};

			const jobs: Promise<void>[] = [];
			for (let i = 0; i < less_styles.length; ++i) {
				jobs[i] = job(i);
			}

			return Deferred.all(jobs);
		}).then(() => {
			const template = meta.domModule.getElementsByTagName("template")[0];
			if (template) return this.compilePolymerSugars(template)
		}).then(() => {
			// Polymer constructor	
			meta.constructor = Polymer(meta.proto);
			return element;
		});
	}
	
	/**
	 * Compile Polymer sugars inside a template
	 */
	compilePolymerSugars(tpl: HTMLTemplateElement) {
		const template = tpl.content;
		
		let node: HTMLElement;
		let wrapper = document.createElement("template");
		
		// Attribute promotion helper
		const promote_attribute = (from: string, to?: string, def?: string) => {
			// Implicit target name
			if (!to) to = from;
			
			// Try from extended form
			const extended = `(${from})`;
			if (node.hasAttribute(extended)) from = extended;
			
			// Get value
			const value = node.getAttribute(from) || def;
			if (value) {
				node.removeAttribute(from);
				wrapper.setAttribute(to, value);
			}
		};
		
		// Move node inside the wrapper
		const promote_node = (wrapper_behaviour: string) => {
			node.parentNode.insertBefore(wrapper, node);
			wrapper.setAttribute("is", wrapper_behaviour);
			wrapper.content.appendChild(node);
			wrapper = document.createElement("template");
		};
		
		// <element [if]="{{cond}}">
		const if_nodes = <NodeListOf<HTMLElement>> template.querySelectorAll("[\\[if\\]]");
		for (let i = 0; i < if_nodes.length; ++i) {
			node = if_nodes[i];
			promote_attribute("[if]", "if", node.textContent);
			promote_node("dom-if");
		}
		
		// <element [repeat]="{{collection}}" filter sort observe>
		const repeat_nodes = <NodeListOf<HTMLElement>>  template.querySelectorAll("[\\[repeat\\]]");
		for (let i = 0; i < repeat_nodes.length; ++i) {
			node = if_nodes[i];
			promote_attribute("[repeat]", "items");
			promote_attribute("filter");
			promote_attribute("sort");
			promote_attribute("observe");
			promote_attribute("id");
			promote_node("dom-repeat");
		}
	}
}
