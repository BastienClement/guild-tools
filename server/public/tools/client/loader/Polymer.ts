import {Component} from "../../utils/DI";
import {Loader} from "./Loader";

@Component
export class PolymerCompiler {
	/**
	 * Compiles sugars available in Polymer templates.
	 * @param dom_module
	 * @param loader
	 */
	public async compile(dom_module: Element, loader: Loader): Promise<void> {
		await this.importExternalLess(dom_module, loader);
		await this.compileLess(dom_module, loader);
		this.compileTemplate(dom_module);
	}

	/**
	 * Loads external LESS stylesheets.
	 * @param dom_module
	 * @param loader
	 */
	private async importExternalLess(dom_module: Element, loader: Loader): Promise<void> {
		// Load external LESS
		let less_ext = <NodeListOf<HTMLLinkElement>> dom_module.querySelectorAll(`link[rel="stylesheet/less"]`);

		if (less_ext.length > 0) {
			const job = (i: number) => {
				let link = less_ext[i];
				return loader.fetch(link.href).then(src => {
					// Check if we need to extract a namespace from the source file
					let ns = link.getAttribute("ns");
					if (ns) {
						let ns_key = `:namespace("${ns}")`;
						let ns_start = src.indexOf(ns_key);
						if (ns_start < 0) throw new Error(`No ${ns_key} in file ${link.href}`);

						let levels = 0;
						let block_start: number, block_end: number;
						reader: for (let o = ns_start + ns_key.length; o < src.length; o++) {
							switch (src[o]) {
								case '{':
									if (levels == 0) block_start = o;
									levels += 1;
									break;

								case '}':
									if (levels == 1) {
										block_end = o;
										break reader;
									}
									levels -= 1;
									break;

								case ' ':
								case '\t':
								case '\f':
								case '\r':
								case '\n':
									continue;

								default:
									if (levels == 0)
										throw new Error(`Unexpected char '${src[o]}' before namespace block opening`);
							}
						}

						src = src.slice(block_start + 1, block_end);
					}

					// Create the style element
					let style = document.createElement("style");
					style.type = "text/less";
					style.innerHTML = src;

					// Replace the <link> tag by an inline <style> one
					link.parentNode.replaceChild(style, link);
				});
			};

			let jobs: Promise<void>[] = [];
			for (let i = 0; i < less_ext.length; i++) {
				jobs[i] = job(i);
			}

			await Promise.all(jobs);
		}
	}

	/**
	 * Compiles LESS source.
	 * @param dom_module
	 * @param loader
	 */
	private async compileLess(dom_module: Element, loader: Loader): Promise<void> {
		let less_styles = <NodeListOf<HTMLStyleElement>> dom_module.querySelectorAll(`style[type="text/less"]`);

		if (less_styles.length > 0) {
			const job = (i: number) => {
				let style = less_styles[i];
				return loader.less.compile(style.innerHTML, loader).then(css => {
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
	}

	/**
	 * Compiles the element template.
	 * @param dom_module
	 */
	private compileTemplate(dom_module: Element) {
		let template = <any> dom_module.getElementsByTagName("template")[0];
		if (template) {
			this.compilePolymerSugars(template.content);
			this.compileAngularNotation(template.content);
		}
	}

	/**
	 * Compiles heavy Polymer sugars, like *for and *if.
	 * @param template
	 */
	private compilePolymerSugars(template: DocumentFragment) {
		let node: HTMLElement;
		let wrapper = document.createElement("template");

		// Attribute promotion helper
		const promote_attribute = (from: string, to?: string, def?: string, addBraces: boolean = false) => {
			// Implicit target name
			if (!to) to = from;

			// Try from extended form
			let extended = `(${from})`;
			if (node.hasAttribute(extended)) from = extended;

			// Get value
			let value = node.getAttribute(from) || def;
			if (value) {
				value = this.compileBindingSugars(value);

				node.removeAttribute(from);
				if (addBraces && !value.match(/\{\{.*\}\}/)) {
					value = `{{${value}}}`;
				}

				((node.tagName == "TEMPLATE") ? node : wrapper).setAttribute(to, value);
			}
		};

		// Move node inside the wrapper
		const promote_node = (wrapper_behaviour: string) => {
			if (node.tagName != "TEMPLATE") {
				node.parentNode.insertBefore(wrapper, node);
				wrapper.setAttribute("is", wrapper_behaviour);
				wrapper.content.appendChild(node);
				wrapper = document.createElement("template");
			} else {
				node.setAttribute("is", wrapper_behaviour);
			}
		};

		// Note: we need to find all interesting nodes before promoting any one of them.
		// If we promote *if nodes before looking for *for ones, it is possible for
		// some of them to get nested inside the wrapper.content shadow tree when we
		// attempt to querySelectorAll and they will not be returned.

		// <element *if="cond">
		let if_nodes = <NodeListOf<HTMLElement>> template.querySelectorAll("*[\\*if]");

		// <element *for="collection" filter sort observe>
		let repeat_nodes = <NodeListOf<HTMLElement>> template.querySelectorAll("*[\\*for]");

		for (let i = 0; i < if_nodes.length; ++i) {
			node = if_nodes[i];
			promote_attribute("*if", "if", node.textContent, true);
			promote_node("dom-if");
		}

		for (let i = 0; i < repeat_nodes.length; ++i) {
			node = repeat_nodes[i];
			promote_attribute("*for", "items", "", true);
			promote_attribute("*filter", "filter");
			promote_attribute("*sort", "sort");
			promote_attribute("*observe", "observe");
			promote_attribute("*id", "id");
			promote_attribute("*as", "as");
			promote_attribute("*index-as", "index-as");
			promote_node("dom-repeat");
		}

		// Transform <meta is="..."> to <meta is="...-provider">
		let meta_is_nodes = <NodeListOf<HTMLMetaElement>> template.querySelectorAll("meta[is]");
		for (let i = 0; i < meta_is_nodes.length; ++i) {
			let meta = meta_is_nodes[i];
			meta.setAttribute("is", meta.getAttribute("is") + "-provider");
		}
		
		// Recurse on children
		let children = <any> template.querySelectorAll("template");
		for (let i = 0; children && i < children.length; ++i) {
			this.compilePolymerSugars(children[i].content);
		}
	}

	/**
	 * Compiles binding sugars.
	 * Transform comparisons operator to method calls.
	 * @param binding
	 */
	private compileBindingSugars(binding: string): string {
		let matches = binding.match(/^([^\s]+)\s*([<=>]=?|!=)\s*([^\s]+)$/);
		if (matches) {
			switch (matches[2]) {
				case "=":
				case "==": return `eq(${matches[1]}, ${matches[3]})`;
				case "!=": return `neq(${matches[1]}, ${matches[3]})`;
				case "<": return `lt(${matches[1]}, ${matches[3]})`;
				case "<=": return `lte(${matches[1]}, ${matches[3]})`;
				case ">": return `gt(${matches[1]}, ${matches[3]})`;
				case ">=": return `gte(${matches[1]}, ${matches[3]})`;
			}
		}
		return binding;
	}

	/**
	 * Compiles Angular2-style template to Polymer.
	 * @param node
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
						node.setAttribute(bind, `{{${this.compileBindingSugars(value) || bind}}}`);
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

	/** Node used for compiling attribute bindings */
	private dummy_node = document.createElement("div");

	/**
	 * Crazy workaround because browsers prevents the creation of attributes with special chars
	 * We need to create a Attr node with a forbidden name and add it to the element.
	 * Since only th HTML parser can create such an Attr node, we need to generate HTML and
	 * then use the instance of this new tag to grab the wanted attribute node and paste it on
	 * the old node.
	 *
	 * @param node
	 * @param attrs
	 */
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

		// Instantiate
		this.dummy_node.innerHTML = tag;
		let new_node = <HTMLElement> this.dummy_node.firstChild;

		// Copy attributes
		for (let attr of attrs) {
			let attr_node = <Attr> new_node.attributes.getNamedItem(`${attr[2]}$`).cloneNode(false);
			attr_node.value = `{{${this.compileBindingSugars(attr[1]) || attr[2]}}}`;
			node.attributes.setNamedItem(attr_node);
			node.removeAttribute(attr[0]);
		}

		// Cleanup
		this.dummy_node.innerHTML = "";
	}
}

