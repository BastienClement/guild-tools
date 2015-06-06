interface Polymer {
	dom: string;
	Class(prototype: any): any;
	(prototype: any): Function;
}

declare var Polymer: Polymer;

interface PolymerElement {}

interface Node {
	appendChild(e: PolymerElement): Node;
	removeChild(e: PolymerElement): Node;
}

interface Document {
	createElement(tag: "template"): HTMLTemplateElement;
}

interface DocumentFragment extends Document {}

interface HTMLTemplateElement extends HTMLElement {
	content: DocumentFragment;
}
