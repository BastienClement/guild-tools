import {Constructor} from "../utils/DI";
import {PolymerElement} from "./PolymerElement"

/**
 * Template of a Polymer element declaration
 */
export type PolymerElementDeclaration = {
	selector: string;
	template?: string;
	extending?: string;
	dependencies?: Constructor<PolymerElement>[];
};

/**
 * Declare a Polymer Element
 */
export function Element(decl: PolymerElementDeclaration) {
	return <T extends PolymerElement>(target: Constructor<T>) => {
		Reflect.defineMetadata("polymer:declaration", decl, target);
		target.prototype.beforeRegister = function() {
			this.is = decl.selector;
			console.log("Called before register");
		};
	};
}
