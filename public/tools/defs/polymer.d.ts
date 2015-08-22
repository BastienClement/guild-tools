interface Polymer {
	(prototype: any): Function;
	Class(prototype: any): any;
	Base: PolymerBase;
	dom: ShadyDOMContructor;

	is<T extends PolymerElement>(el: Node | PolymerElement, ctor: { new (): T; }): boolean;
	cast<T extends PolymerElement>(el: Node | PolymerElement, ctor: { new (): T; }): T;
	enclosing<T extends PolymerElement>(el: Node | PolymerElement, ctor: { new (): T; }): T;
}

interface PolymerBase {
	createdCallback: Function;
	attachedCallback: Function;
	detachedCallback: Function;
	attributeChanged: Function;
	getNativePrototype(tag: string): any;
}

interface ShadyDOMContructor {
	(parent: Node | PolymerElement): ShadyDOM;
	flush(): void;
}

interface ShadyDOM {
	appendChild<T extends Node|PolymerElement>(node: T): T;
	insertBefore<T extends Node|PolymerElement>(node: T, beforeNode: Node | PolymerElement): T;
	removeChild<T extends Node|PolymerElement>(node: T): T;

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

	querySelector<T extends Node|PolymerElement>(selector: string): T;
	querySelectorAll<T extends Node|PolymerElement>(selector: string): T[];

	getDistributedNodes(): Node[];
	getDestinationInsertionPoints(): Node[];

	setAttribute(attribute: string, value: string): void;
	removeAttribute(attribute: string): void;
	classList: DOMTokenList;
}

declare var Polymer: Polymer;

interface PolymerElement {}

interface Node {
	appendChild(e: PolymerElement): Node;
	removeChild(e: PolymerElement): Node;
}

interface NodeSelector {
	querySelector<T extends Node>(selector: string): T;
	querySelectorAll<T extends Node>(selector: string): NodeListOf<T>;
}

interface Element {
    getElementsByTagName(name: "template"): NodeListOf<HTMLTemplateElement>;
}

interface Document {
	createElement(tag: "template"): HTMLTemplateElement;
}

interface HTMLTemplateElement extends HTMLElement {
	content: DocumentFragment;
}

interface DocumentFragment extends Document {}
