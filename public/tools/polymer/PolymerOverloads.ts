import {Constructor} from "../utils/DI";

/**
 * Additional monkey patches for Polymer
 */
export function polymer_overloads() {
	/**
	 * Check if an Node is an instance of the given Polymer element
	 */
	Polymer.is = <T extends PolymerElement>(node: any, ctor: Constructor<T>): node is T => {
		const selector = Reflect.getMetadata<{ selector: string; }>("polymer:declaration", ctor).selector;
		return (<any> node).is == selector;
	};

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
	 * Comparison functions
	 */
	Base.eq = <T>(a: T, b: T) => a === b;
	Base.neq = <T>(a: T, b: T) => a !== b;
	Base.lt = <T>(a: T, b: T) => a < b;
	Base.lte = <T>(a: T, b: T) => a <= b;
	Base.gt = <T>(a: T, b: T) => a > b;
	Base.gte = <T>(a: T, b: T) => a >= b;
}
