import { Component, Injector } from "utils/di";
import { EventEmitter } from "utils/eventemitter";
import { PolymerConstructor, PolymerElement } from "elements/polymer";

interface RoutePattern {
	path: [RegExp, string[]];
	view: PolymerConstructor<any>;
}

@Component
export class Router extends EventEmitter {
	private routes: RoutePattern[] = [];
	
	public config(pattern: string, view: PolymerConstructor<any>) {
		
	}
	
	public static compilePath(path: string): [RegExp, string[]]{
		console.log(path.split(/:[a-z]+\b/));
		return null;
	}
}

/**
 * List of all registered annotations
 */
export const RouteAnnotations = {
	defaultRoute: "",
	routes: new Array<RoutePattern>()
};

/**
 * @Route annotation
 */
export function Route(path: string) {
	return <T extends PolymerElement>(target: PolymerConstructor<T>) => {
		RouteAnnotations.routes.push({
			path: Router.compilePath(path),
			view: target
		});
	}
}