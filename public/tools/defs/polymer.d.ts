interface Polymer {
	(prototype: any): Function;
	Class(prototype: any): any;
	Base: PolymerBase;
	dom: ShadyDOMContructor;

	is<T extends PolymerElement>(el: Node, ctor: { new (): T; }): boolean;
	cast<T extends PolymerElement>(el: Node, ctor: { new (): T; }): T;
	enclosing<T extends PolymerElement>(el: Node, ctor: { new (): T; }): T;
}

interface PolymerBase {
	createdCallback: Function;
	attachedCallback: Function;
	detachedCallback: Function;
	attributeChanged: Function;
	getNativePrototype(tag: string): any;
}

interface ShadyDOMContructor {
	(parent: Node): ShadyDOM;
	<T extends Event>(event: T): ShadyDOM;
	flush(): void;
}

interface ShadyDOM {
	appendChild<T extends Node>(node: T): T;
	insertBefore<T extends Node>(node: T, beforeNode: Node): T;
	removeChild<T extends Node>(node: T): T;

	childNodes: Node[];
	children: Element[];
	parentNode: Node;
	firstChild: Node;
	lastChild: Node;
	firstElementChild: Node;
	lastElementChild: Node;
	previousSibling: Node;
	nextSibling: Node;
	textContent: string;
	innerHTML: string;

	querySelector<T extends Node>(selector: string): T;
	querySelectorAll<T extends Node>(selector: string): T[];

	getDistributedNodes(): Node[];
	getDestinationInsertionPoints(): Node[];

	setAttribute(attribute: string, value: string): void;
	removeAttribute(attribute: string): void;
	classList: DOMTokenList;

	node: HTMLElement;

	// Event
	event: any;
	localTarget: any;
	rootTarget: any;
}

declare var Polymer: Polymer;

interface PolymerElement extends HTMLElement {}

interface PolymerModelEvent<T> extends Event {
	model: { item: T };
}

interface NodeSelector {
	querySelector<T extends Element>(selector: string): T;
	querySelectorAll<T extends Element>(selector: string): NodeListOf<T>;
}

interface Element {
    getElementsByTagName(name: "template"): NodeListOf<HTMLTemplateElement>;
}

interface Document {
	createElement(tag: "template"): HTMLTemplateElement;
}

interface HTMLTemplateElement extends HTMLElement {}

interface DocumentFragment extends Document {
	host: Element;
}
