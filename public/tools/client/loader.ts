import { Component, Constructor } from "utils/di";
import { Deferred } from "utils/deferred";
import { ServiceWorker } from "utils/worker";
import { PolymerElement, PolymerConstructor, PolymerMetadata, apply_polymer_fns } from "elements/polymer";

// Path to the polymer file
const POLYMER_PATH = gt_asset("imports/polymer.html");

// The Response object returned by fetch()
interface FetchResponse {
	text(): Promise<string>;
}

// Alias to the fetch function for type-safety purpose
const fetch: (url: string) => Promise<FetchResponse> = (<any>window).fetch;

// Keep references to already imported HTML document
const imported_documents: Map<string, Promise<Document>> = new Map<any, any>();

// Keep references to already imported LESS stylesheet
const imported_less: Map<string, Promise<void>> = new Map<any, any>();

// Track polymer status
let polymer_loaded = false;
let polymer_autoload: PolymerConstructor<any>[] = [];

/**
 * Resource loading service
 * Handle LESS compilation and Polymer sugars
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
	public fetch(url: string, cache: boolean = true): Promise<string> {
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
		if (fetch) return cache_and_return(fetch(url).then((res) => res.text()))

		// Fallback to XHR
		let defer = new Deferred<string>();
		let xhr = new XMLHttpRequest();

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
	public loadLess(url: string): Promise<void> {
		let promise = imported_less.get(url);
		if (promise) return promise;
		
		promise = (async() => {
			let source = await this.fetch(url);
			let css = await this.compileLess(source);

			let style = document.createElement("style");
			style.innerHTML = css;
			style.setAttribute("data-source", url);
			document.head.appendChild(style);
		})();
		
		imported_less.set(url, promise);
		return promise;
	}

	/**
	 * Handle @import (dynamic) statements
	 */
	private async lessImportDynamics(source: string): Promise<string> {
		// Split the input file on every dynamic import
		let parts = source.split(/@import\s*\(dynamic\)\s*"([^"]*)";?/);

		// No import fournd
		if (parts.length == 1) return source;

		// Fetch imports
		let dyn_imports: Promise<string>[] = [];
		for (let i = 1; i < parts.length; ++i) {
			if (i % 2 == 1) dyn_imports.push(this.fetch(parts[i]));
		}
		
		let dyn_sources = await Promise.all(dyn_imports);
		for (let i = 0; i < dyn_sources.length; i++) {
			parts[i * 2 + 1] = dyn_sources[i];
		}
		
		// Recursive handling of deep @import (dynamic)
		return this.lessImportDynamics(parts.join(""));
	}

	/**
	 * Compile LESS source to CSS
	 */
	private lessWorker = new ServiceWorker(gt_asset("modules/workers/less.js"));
	public async compileLess(source: string): Promise<string> {
		// Prepend the
		source = `
			@import (dynamic) "${gt_asset("/less/lib.less")}";
			${source}
		`;
		
		let source_css = await this.lessImportDynamics(source);
		let result_css = await this.lessWorker.request<string>("compile", source_css);
		
		result_css = result_css.replace(/\/assets\/([^")\s]+)/g, (matched: string, url: string) => {
			return gt_asset(url);
		});
		
		return StyleFix.fix(result_css, true);
	}

	/**
	 * Perform an HTML import
	 */
	public loadDocument(url: string): Promise<Document> {
		let promise = imported_documents.get(url);
		if (promise) return promise;

		let link = document.createElement("link");
		link.rel = "import";
		link.href = url;

		promise = Deferred.onload(link).then((el: any) => {
			const doc = el.import;
			if (!doc) throw new Error(`HTML import of ${url} failed`);
			return el.import;
		});

		imported_documents.set(url, promise);
		document.head.appendChild(link);

		return promise;
	}

	/**
	 * Load and instantiate a Polymer element
	 */
	public async loadElement<T extends PolymerElement>(element: PolymerConstructor<T>): Promise<PolymerConstructor<T>> {
		// Read Polymer metadata
		let meta = Reflect.getMetadata<PolymerMetadata<T>>("polymer:meta", element);
		
		// Check if the element was already loaded once
		if (meta.loaded) {
			return element;
		}
		
		// Ensure that Polymer is loaded
		if (!polymer_loaded) {
			polymer_loaded = true;
			
			//if (localStorage.getItem("polymer.useShadowDOM") == "1")
			(<any>window).Polymer = { dom: "shadow" };
			
			// Load polymer
			await this.loadDocument(POLYMER_PATH);
			
			// Load auto-load elements
			for (let i = 0; i < polymer_autoload.length; i++) {
				await this.loadElement(polymer_autoload[i]);
			}
			polymer_autoload = null;
			
			// Load the requested element
			return this.loadElement(element);
		} else if (!Polymer.is) {
			apply_polymer_fns();
		}

		meta.loaded = true;

		// Load dependencies of the element if any
		if (meta.dependencies) {
			await Promise.all(meta.dependencies.map(dep => this.loadElement(dep)))
		}

		// Load and compile the element template
		if (meta.template) {
			let document = await this.loadDocument(meta.template);
			
			// Find the <dom-module> element
			let domModule = document.querySelector<HTMLElement>(`dom-module[id=${meta.selector}]`);
			if (!domModule) throw new Error(`no <dom-module> found for element <${meta.selector}> in file '${meta.template}'`);
			
			// Compile LESS
			let less_styles = <NodeListOf<HTMLStyleElement>> domModule.querySelectorAll(`style[type="text/less"]`);
			if (less_styles.length > 0) {
				const job = (i: number) => {
					let style = less_styles[i];
					return this.compileLess(style.innerHTML).then(css => {
						let new_style = document.createElement("style");
						new_style.innerHTML = css;

						style.parentNode.insertBefore(new_style, style);
						style.parentNode.removeChild(style);
					});
				};

				let jobs: Promise<void>[] = [];
				for (let i = 0; i < less_styles.length; ++i) {
					jobs[i] = job(i);
				}

				await Promise.all(jobs);
			}

			// Compile template            
			let template = domModule.getElementsByTagName("template")[0];
			if (template) {
				let children = template.content.querySelectorAll("img");
				for (let i = 0; i < children.length; i++) {
					let attrs = children[i].attributes;
					for (let j = 0; j < attrs.length; j++) {
						let attr = attrs[j];
						attr.value = attr.value.replace(/\/assets\/([^")\s]+)/g, function(all, match) { return gt_asset(match); });
					}
				}
				
				this.compilePolymerSugars(template.content);
				this.compileAngularNotation(<any>template.content);
			}
		}

		meta.constructor = Polymer(meta.proto);
		return element;
	}
	
	/**
	 * Register an element to auto load when polymer is loaded
	 */
	public registerPolymerAutoload(ctor: PolymerConstructor<any>) {
		if (polymer_autoload) {
			polymer_autoload.push(ctor);
		} else {
			this.loadElement(ctor);
		}
	}

	/**
	 * Compile Polymer sugars inside a template
	 */
	private compilePolymerSugars(template: DocumentFragment) {
		let node: HTMLElement;
		let wrapper = document.createElement("template");

		// Attribute promotion helper
		const promote_attribute = (from: string, to?: string, def?: string, addBraces: boolean = false) => {
			// Implicit target name
			if (!to) to = from;

			// Try from extended form
			const extended = `(${from})`;
			if (node.hasAttribute(extended)) from = extended;

			// Get value
			let value = node.getAttribute(from) || def;
			if (value) {
				node.removeAttribute(from);
				if (addBraces && !value.match(/^\{\{.*\}\}$/)) {
					value = `{{${value}}}`;
				}
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
		let if_nodes = <NodeListOf<HTMLElement>> template.querySelectorAll("*[\\[if\\]]");
		for (let i = 0; i < if_nodes.length; ++i) {
			node = if_nodes[i];
			promote_attribute("[if]", "if", node.textContent, true);
			promote_node("dom-if");
		}

		// <element [repeat]="{{collection}}" filter sort observe>
		let repeat_nodes = <NodeListOf<HTMLElement>>  template.querySelectorAll("*[\\[repeat\\]]");
		for (let i = 0; i < repeat_nodes.length; ++i) {
			node = repeat_nodes[i];
			promote_attribute("[repeat]", "items", "", true);
			promote_attribute("$filter", "filter");
			promote_attribute("$sort", "sort");
			promote_attribute("$observe", "observe");
			promote_attribute("$id", "id");
			promote_attribute("$as", "as");
			promote_attribute("$index-as", "index-as");
			promote_node("dom-repeat");
		}
	}

	/**
	 * Compile Angular2-style template to Polymer
	 */
	private compileAngularNotation(node: HTMLElement) {
		if (!node) return;

		// Find Angular2-style attributes
		let attrs: [string, string, string][] = [];
		for (let i = 0; node.attributes && i < node.attributes.length; ++i) {
			let attr = node.attributes[i];
			attrs[i] = [attr.name, attr.value, attr.name.slice(1, -1)];
		}

		let children = node.childNodes;
		let attr_bindings_compiled = false;

		for (let a of attrs) {
			let [name, value, bind] = a;
			if (name[0] == "[" || name[0] == "(" || name[0] == "{") {
				switch (name[0]) {
					case "[":
						node.removeAttribute(name);
						node.setAttribute(bind, `{{${value || bind}}}`);
						break;
					case "(":
						node.removeAttribute(name);
						node.setAttribute(`on-${bind}`, value);
						break;
					case "{":
						// Dumb browsers preventing special chars in attribute names
						if (!attr_bindings_compiled) {
							this.compileAttributeBindings(node, attrs);
							attr_bindings_compiled = true;
						}
						break;
				}
			} else if (name[0] == "#") {
				node.removeAttribute(name);
				node.setAttribute("id", name.slice(1));
			} else if (name[0] == ".") {
				node.removeAttribute(name);
				node.classList.add(name.slice(1));
			}
		}

		// Recurse on children
		for (let i = 0; children && i < children.length; ++i) {
			let child: any = children[i];
			this.compileAngularNotation(child.content || child);
		}
	}

	/**
	 * Crazy workaround because browsers prevents the creation of attributes with special chars
	 * We need to create a Attr node with a forbidden name and add it to the element.
	 * Since only th HTML parser can create such an Attr node, we need to generate HTML and
	 * then use the instance of this new tag to grab the wanted attribute node and paste it on
	 * the old node.
	 */
	private dummy_node = document.createElement("div");
	private compileAttributeBindings(node: HTMLElement, attrs: [string, string, string][]) {
		// Removes attributes that are not {} bindings
		attrs = attrs.filter(attr => attr[0][0] == "{");

		// Construct the new tag
		// -> Special case for empty tag
		let tag = node.outerHTML.slice(0, node.outerHTML.indexOf(">") + 1);
		for (let attr of attrs) {
			tag = tag.replace(attr[0], `${attr[2]}$`);
		}
		
		// Replace the element name by <div>
		// Without this, an instance of the element is incorrectly created
		tag = tag.replace(/^<[^\s]+/, "<div");
        
		// Instatiate
		this.dummy_node.innerHTML = tag;
		let new_node = <HTMLElement> this.dummy_node.firstChild;

		// Copy attributes
		for (let attr of attrs) {
			let attr_node = <Attr> new_node.attributes.getNamedItem(`${attr[2]}$`).cloneNode(false);
			attr_node.value = `{{${attr[1] || attr[2]}}}`;
			node.attributes.setNamedItem(attr_node);
			node.removeAttribute(attr[0]);
		}

		// Cleanup
		this.dummy_node.innerHTML = "";
	}
}
