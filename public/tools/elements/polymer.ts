import { Deferred, LazyThen } from "utils/deferred";
import { Queue } from "utils/queue";

/**
 * Dummy class to expose Polymer functions on elements
 */
export class PolymerElement {
	//protected node: PolymerDomNode;
	//protected shadow: PolymerDomNode;
	protected root: DocumentFragment;
	
	/**
	 * The Dollar Helper
	 */
	protected $: PolymerDollarHelper;
	
	/**
	 * Returns the first node in this element's local DOM that matches selector
	 */
	protected $$: (selector: string) => Element;
	
	/**
	 * Toggles the named boolean class on the host element, adding the class if
	 * bool is truthy and removing it if bool is falsey.
	 * If node is specified, sets the class on node instead of the host element.
	 */
	protected toggleClass: (name: string, bool: boolean, node?: Element) => void;
	
	/**
	 * Like toggleClass, but toggles the named boolean attribute.
	 */
	protected toggleAttribute: (name: string, bool: boolean, node?: Element) => void;
	
	/**
	 * Moves a boolean attribute from oldNode to newNode, unsetting the attribute
	 * (if set) on oldNode and setting it on newNode.
	 */
	protected attributeFollows: (name: string, newNode: HTMLElement, oldNode: HTMLElement) => void;
	
	/**
	 * Removes a class from one node, and adds it to another.
	 */
	protected classFollows: (name: string, newNode: HTMLElement, oldNode: HTMLElement) => void;
	
	/**
	 * Force this element to distribute its children to its local dom.
	 * A user should call distributeContent if distribution has been invalidated due to
	 * changes to selectors on child elements that effect distribution that were not made
	 * via Polymer.dom.
	 * For example, if an element contains an insertion point with <content select=".foo">
	 * and a foo class is added to a child, then distributeContent must be called to update
	 * local dom distribution.
	 */
	protected distributeContent: () => void;
	
	protected getContentChildNodes: (selector?: string) => Node[];
	protected getContentChildren: (selector?: string) => Element[];
	
	/**
	 * Fires a custom event.
	 */
	protected fire: PolymerFireInterface;
	
	/**
	 * Calls method asynchronously. If no wait time is specified, runs tasks with microtask timing
	 * (after the current method finishes, but before the next event from the event queue is processed).
	 * Returns a handle that can be used to cancel the task.
	 */
	protected async: (method: string, wait?: number) => PolymerAsyncHandler;
	
	/**
	 * Cancels the identified async task
	 */
	protected cancelAsync: (handler: PolymerAsyncHandler) => void;
	
	/**
	 * Call debounce to collapse multiple requests for a named task into one invocation,
	 * which is made after the wait time has elapsed with no new request.
	 * If no wait time is given, the callback is called at microtask timing
	 * (guaranteed to be before paint).
	 */
	protected debounce: (jobName: string, callback: Function, wait?: number) => void;
	
	/**
	 * Cancels an active debouncer without calling the callback.
	 */
	protected cancelDebouncer: (jobName: string) => void;
	
	/**
	 * Calls the debounced callback immediately and cancels the debouncer.
	 */
	protected flushDebouncer: (jobName: string) => void;
	
	/**
	 * Returns true if the named debounce task is waiting to run.
	 */
	protected isDebouncerActive: (jobName: string) => boolean;
	
	/**
	 * Applies a CSS transform to the specified node, or this element
	 * if no node is specified. transform is specified as a string.
	 */
	protected transform: (transform: string, node?: Element) => void;
	
	/**
	 * Transforms the specified node, or this element if no node is specified. 
	 */
	protected translate3d: (x: string, y: string, z: string, node?: Element) => void;
	
	/**
	 * Dynamically imports an HTML document
	 */
	protected importHref: (href: string, onload: Function, onerror: Function) => void;
}

/**
 * Type of the $ helper on Polymer elements
 */
interface PolymerDollarHelper {
	[key: string]: HTMLElement;
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
 * Interface of a Polymer constructor function
 */
export interface PolymerConstructor<T extends PolymerElement> extends Function {
	new (): T;
	__polymer?: PolymerMetadata<T>;
	__polymer_dependencies?: PolymerConstructor<any>[];
}

/**
 * The Polymer returned to replace the original class constructor
 */
export interface PolymerProxy<T extends PolymerElement> extends PolymerConstructor<T> {
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
	dependencies: PolymerConstructor<any>[];
	loaded: boolean;
	domModule?: HTMLElement;
	constructor?: Function;
}

/**
 * Declare a Polymer Element
 */
export function Element(selector: string, template: string) {
	return <T extends PolymerElement>(target: PolymerConstructor<T>) => {
		// Transpose instance variables on prototype
		target.call(target.prototype);
		target.prototype.is = selector;
		target.prototype.factoryImpl = target.prototype.factory;
		
		target.prototype.createdCallback = function() {
			Polymer.Base.createdCallback.apply(this, arguments);
		};
		
		// Reference to the element metadata object
		let meta: PolymerMetadata<T>;

		// The placeholder during element loading
		const proxy: PolymerProxy<T> = <any> function() {
			if (!meta.constructor) throw new Error("Polymer element is not yet loaded");
			return meta.constructor.apply(Object.create(meta.constructor.prototype), arguments);
		};
		
		// Create the metadata objet
		meta = proxy.__polymer = {
			selector: selector,
			template: template,
			proto: target.prototype,
			dependencies: target.__polymer_dependencies,
			loaded: false
		};
		
		// Transpose static members on the proxy function
		for (let key in target) {
			if (target.hasOwnProperty(key)) {
				proxy[key] = (<PolymerProxy<T>> target)[key];
			}
		}
		
		return <any> proxy;
	};
}

/**
 * Delcare Polymer element dependencies
 */
export function Dependencies(...dependencies: PolymerConstructor<any>[]) {
	return <T extends PolymerElement>(target: PolymerConstructor<T>) => {
		const meta = target.__polymer;
		if (!meta) (<any> target).__polymer_dependencies = dependencies
		else meta.dependencies = dependencies;
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

/**
 * Apply additionnal functions on the Polymer object
 */
export function apply_polymer_fns() {
	Polymer.is = <T extends PolymerElement>(el: Node, ctor: PolymerConstructor<T>) => {
		return Object.getPrototypeOf(el) === Polymer.Base.getNativePrototype(ctor.__polymer.selector);
	};
	
	Polymer.cast = <T extends PolymerElement>(el: Node, ctor: PolymerConstructor<T>) => {
		if (Polymer.is(el, ctor)) {
			return el;
		} else {
			throw new TypeError(`Node <${el.nodeName}> is not castable to <${ctor.__polymer.selector}>`);
		}
	};
}