interface Polymer {
	dom: string;
	Class(prototype: any): any;
	(prototype: any): Function;
}

interface PolymerElement {}

interface Node {
	appendChild(e: PolymerElement): Node;
	removeChild(e: PolymerElement): Node;
}

declare var Polymer: Polymer;
