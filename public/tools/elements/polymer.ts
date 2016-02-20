import { Constructor, DefaultInjector } from "utils/di";
import { EventEmitter } from "utils/eventemitter";
import { Service } from "utils/service";
import { Application } from "client/main";
import { Loader } from "client/loader";

//
// The HTMLElement Hack
//

let global: any = window;
let old_HTMLElement = global.HTMLElement;
global.HTMLElement = function HTMLElement() {};

function restore_HTMLElement() {
	global.HTMLElement = old_HTMLElement;
}

/**
 * Dummy class to expose Polymer functions on elements
 */
export abstract class PolymerElement extends HTMLElement {
	// Dummy property used to restore the HTMLElement
	private static dummy = restore_HTMLElement();

	//protected root: DocumentFragment;
	protected node: ShadyDOM;
	protected shadow: ShadyDOM;

	// Remove typechecking on the any property.
	// Many custom elements are using id as number.
	public id: any;

	/**
	 * Reference to the GT Application object
	 * The injector always inject the global Application
	 */
	protected app: Application;

	/**
	 * The Dollar Helper
	 */
	protected $: any;

	/**
	 * Convenience method to run querySelector on this local DOM scope.
	 * This function calls Polymer.dom(this.root).querySelector(slctr).
	 */
	protected $$: (selector: string) => Element;

	/**
	 * Removes an item from an array, if it exists.
	 *
	 * If the array is specified by path, a change notification is generated,
	 * so that observers, data bindings and computed properties watching that path can update.
	 *
	 * If the array is passed directly, no change notification is generated.
	 */
	protected arrayDelete: <T>(path: string | Array<T>, item: any) => Array<T>;

	/**
	 * Runs a callback function asyncronously.
	 * By default (if no waitTime is specified), async callbacks are run at microtask timing,
	 * which will occur before paint.
	 */
	protected async: (method: string, wait?: number) => PolymerAsyncHandler;

	/**
	 * Removes an HTML attribute from one node, and adds it to another.
	 */
	protected attributeFollows: (name: string, newNode: HTMLElement, oldNode: HTMLElement) => void;

	/**
	 * Cancels an async operation started with async.
	 */
	protected cancelAsync: (handler: PolymerAsyncHandler) => void;

	/**
	 * Cancels an active debouncer. The callback will not be called.
	 */
	protected cancelDebouncer: (jobName: string) => void;

	/**
	 * Removes a class from one node, and adds it to another.
	 */
	protected classFollows: (name: string, newNode: HTMLElement, oldNode: HTMLElement) => void;

	/**
	 * Call debounce to collapse multiple requests for a named task into one invocation which is made
	 * after the wait time has elapsed with no new request.
	 *
	 * If no wait time is given, the callback will be called at microtask timing (guaranteed before paint).
	 */
	protected debounce: (jobName: string, callback: Function, wait?: number) => void;

	/**
	 * Converts a string to a typed value.
	 *
	 * This method is called by Polymer when reading HTML attribute values to JS properties.
	 * Users may override this method on Polymer element prototypes to provide deserialization for custom types.
	 * Note, the type argument is the value of the type field provided in the properties configuration object
	 * for a given property, and is by convention the constructor for the type to deserialize.
	 *
	 * Note: The return value of undefined is used as a sentinel value to indicate the attribute should be removed.
	 */
	protected deserialize: <T>(value: string, type: any) => T;

	/**
	 * Force this element to distribute its children to its local dom.
	 *
	 * A user should call distributeContent if distribution has been invalidated due to changes to
	 * selectors on child elements that effect distribution that were not made via Polymer.dom.
	 *
	 * For example, if an element contains an insertion point with <content select=".foo"> and a foo
	 * class is added to a child, then distributeContent must be called to update local dom distribution.
	 */
	protected distributeContent: (updateInsertionPoints?: boolean) => void;

	/**
	 * Polyfill for Element.prototype.matches, which is sometimes still prefixed.
	 */
	protected elementMatches: (selector: string, node: Element) => boolean;

	/**
	 * Dispatches a custom event with an optional detail value.
	 */
	public fire: PolymerFireInterface;

	/**
	 * Immediately calls the debouncer callback and inactivates it.
	 */
	protected flushDebouncer: (jobName: string) => void;

	/**
	 * Convienence method for reading a value from a path.
	 * Note, if any part in the path is undefined, this method returns undefined
	 * (this method does not throw when dereferencing undefined paths).
	 */
	protected 'get': <T>(path: string | Array<(string | number)>, root?: any) => T;

	/**
	 * Returns the computed style value for the given property.
	 */
	protected getComputedStyleValue: (property: string) => string;

	/**
	 * Returns a list of nodes distributed to this element's <content>.
	 *
	 * If this element contains more than one <content> in its local DOM,
	 * an optional selector may be passed to choose the desired content.
	 */
	protected getContentChildNodes: (selector?: string) => Node[];

	/**
	 * Returns a list of element children distributed to this element's <content>.
	 *
	 * If this element contains more than one <content> in its local DOM,
	 * an optional selector may be passed to choose the desired content.
	 *
	 * This method differs from getContentChildNodes in that only elements are returned.
	 */
	protected getContentChildren: (selector?: string) => HTMLElement[];

	/**
	 * Returns a list of elements that are the effective children.
	 *
	 * The effective children list is the same as the element's children
	 * except that any <content> elements are replaced with the list of
	 * elements distributed to the <content>.
	 */
	protected getEffectiveChildren: () => HTMLElement[];

	/**
	 * Returns a list of nodes that are the effective childNodes.
	 *
	 * The effective childNodes list is the same as the element's childNodes
	 * except that any <content> elements are replaced with the list of nodes
	 * distributed to the <content>, the result of its getDistributedNodes method.
	 */
	protected getEffectiveChildNodes: () => Node[];

	/**
	 * Returns a string of text content that is the concatenation of the
	 * text content's of the element's effective childNodes
	 * (the elements returned by getEffectiveChildNodes.
	 */
	protected getEffectiveTextContent: () => string;

	/**
	 * Convenience method for importing an HTML document imperatively.
	 *
	 * This method creates a new <link rel="import"> element with the provided URL
	 * and appends it to the document to start loading. In the onload callback,
	 * the import property of the link element will contain the imported document contents.
	 */
	protected importHref: (href: string, onload: Function, onerror: Function, async?: boolean) => HTMLLinkElement;

	/**
	 * Calls importNode on the content of the template specified
	 * and returns a document fragment containing the imported content.
	 */
	protected instanceTemplate: (template: HTMLTemplateElement) => DocumentFragment;

	/**
	 * Returns whether a named debouncer is active.
	 */
	protected isDebouncerActive: (jobName: string) => boolean;

	/**
	 * Checks whether an element is in this element's light DOM tree.
	 */
	protected isLightDescendant: (node: Node) => boolean;

	/**
	 * Checks whether an element is in this element's local DOM tree.
	 */
	protected isLocalDescendant: (node: HTMLElement) => boolean;

	/**
	 * Aliases one data path as another, such that path notifications
	 * from one are routed to the other.
	 */
	protected linkPaths: (to: string, from: string) => void;

	/**
	 * Convenience method to add an event listener on a given element,
	 * late bound to a named method on this element.
	 */
	protected listen: (element: Element, event: string, method: string) => void;

	/**
	 * Notify that a path has changed.
	 *
	 * Returns true if notification actually took place, based on
	 * a dirty check of whether the new value was already known.
	 */
	protected notifyPath: (path: string, value: any, fromAbove?: boolean) => boolean;

	/**
	 * Notify that an array has changed.
	 * TODO: typing for splices
	 */
	protected notifySplices: (path: string, splices: any[]) => void;

	/**
	 * Removes an item from the end of array at the path specified.
	 * This method notifies other paths to the same array that a splice occurred to the array.
	 */
	protected pop: <T>(path: string) => T;

	/**
	 * Adds items onto the end of the array at the path specified.
	 * This method notifies other paths to the same array that a splice occurred to the array.
	 */
	protected push: (path: string, ...values: any[]) => number;

	protected queryAllEffectiveChildren: (selector: String) => any;

	protected queryEffectiveChildren: (selector: String) => any;

	/**
	 * Rewrites a given URL relative to the original location of the document containing
	 * the dom-module for this element. This method will return the same URL before and after vulcanization.
	 */
	protected resolveUrl: (url: string) => string;

	/**
	 * Apply style scoping to the specified container and all its descendants.
	 *
	 * If shouldObserve is true, changes to the container are monitored via
	 * mutation observer and scoping is applied.
	 *
	 * This method is useful for ensuring proper local DOM CSS scoping for elements
	 * created in this local DOM scope, but out of the control of this element
	 * (i.e., by a 3rd-party library) when running in non-native Shadow DOM environments.
	 */
	protected scopeSubtree: (container: Element, shouldObserve?: boolean) => void;

	/**
	 * This method is called by Polymer when setting JS property values to HTML attributes.
	 * Users may override this method on Polymer element prototypes to provide serialization for custom types.
	 */
	protected serialize: <T>(value: T) => string;

	/**
	 * Convienence method for setting a value to a path and notifying any elements bound to the same path.
	 */
	protected 'set': (path: string | Array<(string | number)>, value: any, root?: any) => void;

	/**
	 * Override scrolling behavior to all direction, one direction, or none.
	 */
	protected setScrollDirection: (direction: "all" | "x" | "y" | "none", node?: HTMLElement) => void;

	/**
	 * Removes an item from the beginning of array at the path specified.
	 * This method notifies other paths to the same array that a splice occurred to the array.
	 */
	protected shift: <T>(path: string) => T;

	/**
	 * Starting from the start index specified, removes 0 or more items from the
	 * array and inserts 0 or more new itms in their place.
	 * This method notifies other paths to the same array that a splice occurred to the array.
	 */
	protected splice: (path: string, start: number, deleteCount: number, ...newElements: any[]) => any[];

	/**
	 * Like toggleClass, but toggles the named boolean attribute.
	 */
	protected toggleAttribute: (name: string, bool?: boolean, node?: HTMLElement) => void;

	/**
	 * Toggles the named boolean class on the host element, adding the class if
	 * bool is truthy and removing it if bool is falsey.
	 * If node is specified, sets the class on node instead of the host element.
	 */
	protected toggleClass: (name: string, bool?: boolean, node?: HTMLElement) => void;

	/**
	 * Applies a CSS transform to the specified node, or this element
	 * if no node is specified. transform is specified as a string.
	 */
	protected transform: (transform: string, node?: HTMLElement) => void;

	/**
	 * Transforms the specified node, or this element if no node is specified.
	 */
	protected translate3d: (x: string, y: string, z: string, node?: HTMLElement) => void;

	/**
	 * Removes a data path alias previously established with linkPaths.
	 *
	 * Note, the path to unlink should be the target (to) used when linking the paths.
	 */
	protected unlinkPaths: (path: string) => void;

	/**
	 * Convenience method to remove an event listener from a given element,
	 * late bound to a named method on this element.
	 */
	protected unlisten: (node: Element, eventName: string, methodName: string) => void;

	/**
	 * Adds items onto the beginning of the array at the path specified.
	 * This method notifies other paths to the same array that a splice occurred to the array.
	 */
	protected unshift: (path: string, ...values: any[]) => number;


	/**
	 * Re-evaluates and applies custom CSS properties based on dynamic changes to this
	 * element's scope, such as adding or removing classes in this element's local DOM.
	 * For performance reasons, Polymer's custom CSS property shim relies on this explicit
	 * signal from the user to indicate when changes have been made that affect the values
	 * of custom properties.
	 */
	protected updateStyles: () => void;

	/**
	 * Return the element whose local dom within which this element is contained.
	 */
	protected host: <T>(ctor: Constructor<T>) => T;
}

/**
 * Interface of PolymerElement#fire
 */
interface PolymerFireInterface {
	(type: string, options?: PolymerFireOptions): void;
	(type: string, details: any, options?: PolymerFireOptions): void;
}

/**
 * PolymerElement#fire options object
 */
interface PolymerFireOptions {
	node?: HTMLElement;
	bubbles?: boolean;
	cancelable?: boolean;
}

/**
 * Dummy interface for the opaque type of the value returned by PolymerElement#async
 */
interface PolymerAsyncHandler {}

/**
 * The Polymer returned to replace the original class constructor
 */
export interface PolymerProxy<T extends PolymerElement> extends Constructor<T> {
	new (): T;
	[key: string]: any;
}

/**
 * Metadata about the Polymer element
 */
export interface PolymerMetadata<T extends PolymerElement> {
	selector: string;
	template: string;
	proto: any;
	dependencies: Constructor<any>[];
	loaded: boolean;
	domModule?: HTMLElement;
	constructor?: Function;
	base?: Constructor<any>;
}

/**
 * Polymer powered events
 */
export interface PolymerModelEvent<T> extends Event {
	model: { item: T };
}

/**
 * Registration of DI-enabled properties
 */
interface InjectionBinding {
	ctor: Constructor<any>;
	property: string;
}

interface EventMapping {
	[event: string]: string | boolean;
}

interface ElementBindings {
	[property: string]: EventMapping;
}

/**
 * Declare a Polymer Element
 */
export function Element(selector: string, template?: string, ext?: string) {
	return <T extends PolymerElement>(target: Constructor<T>) => {
		// Register the element selector
		target.prototype.is = selector;

		// Register the extension of native element
		if (ext) target.prototype.extends = ext;

		// Define custom sugars and perform dependency injection
		target.prototype.createdCallback = function() {
			// Construct a dummy object for obtaining default properties values
			let props = Object.create(null);

			// Perform injections
			// Since this is the first thing done, we can be sure that injected objects are available
			// at any moment inside the object (including Polymer own initialization)
			let inject_bindings = Reflect.getMetadata<InjectionBinding[]>("polymer:injects", target.prototype);
			if (inject_bindings) {
				for (let binding of inject_bindings) {
					props[binding.property] = DefaultInjector.get(binding.ctor);
				}
			}

			// Automatically inject the Application
			props.app = DefaultInjector.get<Application>(Application);

			// Call the original constructor
			// Elements *must not* extends the default TypeScript constructor
			// By default, only properties initialization is performed
			target.call(props);

			// Since Polymer sometimes calls ready() before returning from
			// callbackCreated, this method ensure that the initialization
			// is complete before calling ready()
			let committed = false;
			const init_commit = () => {
				if (committed) return;
				else committed = true;

				// Define custom sugars
				Object.defineProperty(this, "node", {
					get: function() { return Polymer.dom(<any> this); }
				});

				Object.defineProperty(this, "shadow", {
					get: function() { return Polymer.dom(<any> this.root); }
				});

				// Copy injected components on the final object
				for (let key in props) {
					if (this[key] === void 0 && !this.properties[key]) {
						this[key] = props[key];
						delete props[key];
					}
				}

				// If a custom initializer is defined, call it
				// When this function is called, default argument values are not
				// yet available. On the other hand, this function can override them.
				if (this.init) this.init();
			};

			// Hook the ready callback and apply default values obtained
			// from the constructor. Deferring this to the ready event
			// ensure that polymer properly notify listeners.
			let ready = this.ready;
			this.ready = () => {
				// Ensure initialization is done
				init_commit();

				// Copy default values
				for (let key in props) {
					// Ensure no one define the value before
					if (this[key] === void 0) {
						this[key] = props[key];
					}
				}

				// Call the old ready function
				if (ready) ready.call(this);
			};

			// Call polymer constructor
			// Elements *must not* use the created() callback either.
			// Use init() instead
			Polymer.Base.createdCallback.apply(this, arguments);

			// Finalize intialization
			init_commit();
		};

		// When the element is attached, register every listener defined using
		// the @On annotation. Also call the constructor if not yet done.
		target.prototype.attachedCallback = function() {
			Polymer.Base.attachedCallback.apply(this, arguments);

			// Handler for binding property
			const create_bind_handler = (property: string, emitter: any) => {
				let parts = property.match(/^(.*)\|(.*)$/);
				this[parts[2]] = emitter[parts[1]];
				return function(value: any) { this[parts[2]] = value; };
			};

			// Attach events
			let bindings = Reflect.getMetadata<ElementBindings>("polymer:bindings", target.prototype);
			if (bindings) {
				for (let property in bindings) {
					// Get the EventEmitter object and ensure it is the correct type
					let emitter = <EventEmitter> this[property];
					if (!(emitter instanceof EventEmitter)) continue;

					let mapping = bindings[property];
					for (let event in mapping) {
						let entry = mapping[event];
						let handler: string = <string> (mapping[event] === true ? event : mapping[event]);
						let fn = (handler.slice(0, 5) == "bind@") ? create_bind_handler(handler.slice(5), emitter) : this[handler];
						if (typeof fn == "function") {
							emitter.on(event, fn, this);
						}
					}
				}
			}

			// Attach to services
			let injects = Reflect.getMetadata<InjectionBinding[]>("polymer:injects", target.prototype);
			if (injects) {
				for (let binding of injects) {
					let injected: Service = this[binding.property]
					if (injected instanceof Service) {
						injected.attachListener(this);
					}
				}
			}
		};

		target.prototype.detachedCallback = function() {
			Polymer.Base.detachedCallback.apply(this, arguments);

			// Detach from services
			let injects = Reflect.getMetadata<InjectionBinding[]>("polymer:injects", target.prototype);
			if (injects) {
				for (let binding of injects) {
					let injected: Service = this[binding.property]
					if (injected instanceof Service) {
						injected.detachListener(this);
					}
				}
			}

			// Detach events
			const bindings = Reflect.getMetadata<ElementBindings>("polymer:bindings", target.prototype);
			if (bindings) {
				for (let property in bindings) {
					// Get the EventEmitter object and ensure it is the correct type
					let emitter = <EventEmitter> this[property];
					if (!(emitter instanceof EventEmitter)) continue;

					let mapping = bindings[property];
					for (let event in mapping) {
						let handler = mapping[event] === true ? event : <string> mapping[event];
						let fn = this[handler];
						if (typeof fn == "function") {
							emitter.off(event, fn, this);
						}
					}
				}
			}
		};

		// Reference to the element metadata object
		let meta: PolymerMetadata<T>;

		// The placeholder during element loading
		let proxy: PolymerProxy<T> = <any> function() {
			if (!meta.constructor) throw new Error("Polymer element is not yet loaded");
			return meta.constructor.apply(Object.create(meta.constructor.prototype), arguments);
		};

		// Get metadata object or create it
		meta = Reflect.getMetadata("polymer:meta", target) || <any> {};
		meta.selector = selector;
		meta.template = template;
		meta.base = target;
		meta.proto = target.prototype;
		meta.loaded = false;

		// Copy it on the proxy
		Reflect.defineMetadata("polymer:meta", meta, proxy);
		Reflect.deleteMetadata("polymer:meta", target);

		// Transpose remaining metadatas
		for (let key of Reflect.getOwnMetadataKeys(target)) {
			Reflect.defineMetadata(key, Reflect.getMetadata(key, target), proxy);
		}

		// Transpose static members on the proxy function
		Object.getOwnPropertyNames(target).forEach(prop => {
			let desc = Object.getOwnPropertyDescriptor(target, prop);
			Object.defineProperty(proxy, prop, desc);
		});

		// There is no attached template, load the element as soon as polymer is loaded
		if (!template) {
			DefaultInjector.get<Loader>(Loader).registerPolymerAutoload(proxy);
		}

		return <any> proxy;
	};
}

/**
 * Declare a Polymer data provider
 */
export function Provider(selector: string) {
	return Element(selector, null, "meta");
}

/**
 * Delcare Polymer element dependencies
 */
export function Dependencies(...dependencies: (Constructor<any> | { prototype: Service })[]) {
	return <T extends PolymerElement>(target: Constructor<T>) => {
		const meta: PolymerMetadata<T> = Reflect.getMetadata("polymer:meta", target) || <any>{};
		//noinspection TypeScriptUnresolvedVariable
		meta.dependencies = <any> dependencies.filter(d => !(d.prototype instanceof Service));
		Reflect.defineMetadata("polymer:meta", meta, target);
	};
}

/**
 * Declare a Polymer Property
 */
interface PolymerPropertyConfig {
	reflect?: boolean;
	readOnly?: boolean;
	notify?: boolean;
	computed?: string;
	observer?: string;
}

export function Property(config: PolymerPropertyConfig): (t: any, p: string) => void;
export function Property(target: any, property: string): void;
export function Property(target: any, property: string, config: PolymerPropertyConfig): void;

export function Property<T>(target?: any, property?: string, config: PolymerPropertyConfig = {}): any {
	// Called with a config object
	if (!(target instanceof PolymerElement)) {
		return (t: any, p: string) => Property(t, p, target);
	}

	if (!target.properties) target.properties = {};

	// Alias reflect -> reflectToAttribute
	if (config.reflect) {
		(<any> config).reflectToAttribute = true;
	}

	// Transform getter to match Poylmer computed property style
	if (config.computed) {
		try {
			const generator = Object.getOwnPropertyDescriptor(target, property).get;
			const updater_key = `_${property.replace(/\W/g, "_")}`;
			target[updater_key] = generator;
			delete target[property];
			config.computed = `${updater_key}(${config.computed.replace(/\s+/g, ",")})`;
		} catch (e) {
			console.error(`Failed to generate computed property '${property}'`);
			throw e;
		}
	}

	// Get type from Typescript annotations
	if (typeof config == "object" && !(<any> config).type) {
		(<any> config).type = Reflect.getMetadata<any>("design:type", target, property);
	}

	target.properties[property] = config;
}

/**
 * Declare a Polymer Property
 */
export function Listener(...events: string[]) {
	return (target: any, property: string) => {
		if (!target.listeners) target.listeners = {};
		for (let event of events) target.listeners[event] = property;
	};
}

/**
 * Declare a Polymer dependency injection
 */
export function Inject<T>(target: any, property: string) {
	// Get the field type constructor
	const ctor = Reflect.getMetadata<Constructor<any>>("design:type", target, property);

	// Get the list of injections for this element and insert a new one
	let injects = Reflect.getMetadata<InjectionBinding[]>("polymer:injects", target) || [];
	injects.push({ ctor, property });
	Reflect.defineMetadata("polymer:injects", injects, target);
}

/**
 * Normalize mapping
 */
type ExtendedMapping = EventMapping | string[] | string;
function normalize_mapping(mapping: ExtendedMapping): EventMapping {
	if (typeof mapping === "string") {
		return { [mapping]: true };
	} else if (Array.isArray(mapping)) {
		const norm: EventMapping = {};
		mapping.forEach(k => norm[k] = true);
		return norm;
	} else {
		return mapping;
	}
}

/**
 * Declare event biding with externals modules
 */
export function On(m: ExtendedMapping) {
	const mapping = normalize_mapping(m);
	return (target: any, property: string) => {
		let bindings = Reflect.getMetadata<ElementBindings>("polymer:bindings", target) || {};
		bindings[property] = bindings[property] || {};
		for (let key in mapping) {
			bindings[property][key] = mapping[key];
		}
		Reflect.defineMetadata("polymer:bindings", bindings, target);
	};
}

/**
 * Same as @On but automatically adjust for @Notify naming convention
 */
export function Watch(m: ExtendedMapping) {
	const mapping = normalize_mapping(m);
	const ajusted_mapping: { [key: string]: any } = {}
	for (let key in mapping) {
		ajusted_mapping[`${key}-updated`] = mapping[key];
	}
	return On(ajusted_mapping);
}

/**
 * Same as @Watch but additionally automatically set own property
 */
export function Bind(m: ExtendedMapping) {
	const mapping = normalize_mapping(m);
	const ajusted_mapping: { [key: string]: any } = {}
	for (let key in mapping) {
		if (typeof mapping[key] == "boolean") mapping[key] = key;
		ajusted_mapping[`${key}-updated`] = `bind@${key}|${mapping[key]}`;
	}
	return On(ajusted_mapping);
}

/**
 * Apply additionnal functions on the Polymer object
 */
export function apply_polymer_fns() {
	/**
	 * Check if an Node is an instance of the given Polymer element
	 */
	Polymer.is = <T extends PolymerElement>(node: any, ctor: Constructor<T>): node is T => {
		const selector = Reflect.getMetadata<{ selector: string; }>("polymer:meta", ctor).selector;
		return (<any> node).is == selector;
	};

	/**
	 * Type-safe cast of Node to Polymer elements
	 */
	Polymer.cast = <any> (<T extends PolymerElement>(node: Node, ctor: Constructor<T>) => {
		if (Polymer.is(node, ctor)) {
			return node;
		} else {
			const selector = Reflect.getMetadata<{ selector: string; }>("polymer:meta", ctor).selector;
			throw new TypeError(`Node <${node.nodeName}> is not castable to <${selector}>`);
		}
	});

	/**
	 * Find the closed parent node of a given type
	 * TODO: prevent crossing shadow-dom boundaries
	 */
	Polymer.enclosing = <any> (<T extends PolymerElement>(node: Node, ctor: Constructor<T>) => {
		do {
			node = node.parentNode;
		} while (node && !Polymer.is(node, ctor));
		return node;
	});

	const Base: any = Polymer.Base;

	/**
	 * Find the closed host element of a given type
	 */
	Base.host = function <T extends PolymerElement>(ctor: Constructor<T>): T {
		return Polymer.enclosing(this, ctor);
	};

	/**
	 * == function
	 */
	Base.eq = function<T>(a: T, b: T) {
		return a === b;
	};

	/**
	 * != function
	 */
	Base.neq = function<T>(a: T, b: T) {
		return a !== b;
	};

	/**
	 * < function
	 */
	Base.lt = function<T>(a: T, b: T) {
		return a < b;
	};

	/**
	 * <= function
	 */
	Base.lte = function<T>(a: T, b: T) {
		return a <= b;
	};

	/**
	 * > function
	 */
	Base.gt = function<T>(a: T, b: T) {
		return a > b;
	};

	/**
	 * >= function
	 */
	Base.gte = function<T>(a: T, b: T) {
		return a >= b;
	};
}
