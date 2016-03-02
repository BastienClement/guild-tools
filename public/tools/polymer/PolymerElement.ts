import {Constructor} from "../utils/DI";
import {Application} from "../client/Application";
import {DefaultInjector} from "../utils/DI";
import {Loader} from "../client/loader/Loader";

//
// The HTMLElement Hack
//

let global: any = window;
let old_HTMLElement = global.HTMLElement;
global.HTMLElement = function PolymerElement() {};

function restore_HTMLElement() {
	global.HTMLElement = old_HTMLElement;
}

/** Stack of dynamic constructor targets */
const dyn_target = <any[]> [];

/**
 * Manages polymer dynamic constructor target.
 * Polymer @Element annotation will call this to create a createdCallback
 * that will automatically save the element instance in the dyn_target stack
 * and remove it when the lifecycle callback returns.
 */
export const PolymerDynamicTarget = (self: any, block: () => void) => {
	dyn_target.push(self);
	block.apply(self, arguments);
	dyn_target.pop();
};

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

	/**
	 * Dummy constructor for Polymer elements.
	 * This constructor will no call its super class initializer.
	 * Instead it will return the top most scope from the dyn_target stack thus
	 * setting the this variable in the child constructor to the correct polymer
	 * instance.
	 * Child constructor will effectively be invoked on the element instance.
	 */
	constructor() {
		// Dummy code to make Typescript compiler happy
		if (0 > 1) super();

		// Element is being instantiated with new
		if (dyn_target.length < 1) debugger;

		return dyn_target[dyn_target.length - 1];
	}

	static "new"(): any {
		return DefaultInjector.get(Loader).createElement(<any> this);
	}
}


/**
 * Interface of PolymerElement#fire
 */
type PolymerFireInterface = {
	(type: string, options?: PolymerFireOptions): void;
	(type: string, details: any, options?: PolymerFireOptions): void;
}

/**
 * PolymerElement#fire options object
 */
type PolymerFireOptions = {
	node?: HTMLElement;
	bubbles?: boolean;
	cancelable?: boolean;
}

/**
 * Dummy interface for the opaque type of the value returned by PolymerElement#async
 */
type PolymerAsyncHandler = {};
