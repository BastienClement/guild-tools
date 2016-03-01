import {Component, Constructor} from "../../utils/DI";
import {Loader} from "../loader/Loader";
import {EventEmitter} from "../../utils/EventEmitter";

/**
 * One specific route configuration
 */
interface RoutePattern {
	pattern: RegExp;
	tags: string[];
	view: Constructor<PolymerElement>;
}

/**
 * The object used to store route arguments
 */
interface ArgumentsObject {
	[arg: string]: string;
}

/**
 * Interface of route declaration
 */
export type RouteDeclaration = [string, Constructor<PolymerElement>];

/**
 * Extract parameters names from view path and construct
 * an equivalent regular expression for matching the path
 */
function compilePattern(path: string): [RegExp, string[]] {
	// Remove trailing slashes and make parens non-capturing
	// to prevent breaking tags capture
	path = path.replace(/\/$/, "").replace(/\(/g, "(?:");

	// Extract tag names
	let tags = (path.match(/[^?]:(?:[^:]+:)?([a-z_0-9\-]+)/g) || []).map(tag => tag.slice(tag.lastIndexOf(":") + 1));

	// Replace tags with capturing placeholders
	path = path.replace(/([^?]):(?:([^:]+):)?[a-z_0-9\-]+/g, (all, prefix, pattern) => {
		return `${prefix}(${ pattern || "[^/]+" })`;
	});

	return [new RegExp(`^${path}/?$`), tags];
}

/**
 * Routing component for GuildTools
 */
@Component
export class Router extends EventEmitter {
	/**
	 * Routes definitions
	 */
	private routes: RoutePattern[] = [];

	// Current main-view information
	public activePath: string;
	public activeArguments: ArgumentsObject;
	public activeView: Constructor<PolymerElement>;

	/**
	 * User will be redirected to this path if no view matches the current path
	 */
	public fallback: string;

	/**
	 * Prevent navigation if set
	 */
	private locked: boolean = false;

	constructor(private loader: Loader) {
		super();
		window.addEventListener("popstate", () => this.update());
	}

	/**
	 * Load routes from a definition array
	 */
	public loadRoutes(routes: RouteDeclaration[]) {
		for (let [path, view] of routes) {
			let [pattern, tags] = compilePattern(path);
			this.routes.push({ pattern, tags, view });
		}
	}

	/**
	 * Notify listeners that the current route changed.
	 */
	private notify() {
		this.emit("route-updated", this.activePath, this.activeArguments, this.activeView);
	}

	/**
	 * Update the router state to match the current path
	 */
	public update() {
		// Current path
		let path = location.pathname;
		if (path == this.activePath) return;

		// Trim trailing slash if any
		if (path.match(/^\/.*\/$/)) {
			return this.goto(path.slice(0, -1));
		}

		// Search matching view
		for (let route of this.routes) {
			let matches = path.match(route.pattern);
			if (matches) {
				// Construct argument object
				let args = <ArgumentsObject> {};
				for (let i = 0; i < route.tags.length; ++i) {
					args[route.tags[i]] = matches[i + 1];
				}

				// Success
				this.activePath = path;
				this.activeArguments = args;
				this.activeView = route.view;
				this.notify();
				return;
			}
		}

		// Nothing found, go to fallback
		if (this.fallback && path != this.fallback) {
			return this.goto(this.fallback, true);
		} else {
			this.activePath = null;
			this.activeArguments = null;
			this.activeView = null;
			this.notify();
		}
	}

	/**
	 * Navigation
	 */
	public goto(path: string, replace: boolean = false) {
		if (this.locked) return;

		if (replace) history.replaceState(null, "", path);
		else history.pushState(null, "", path);

		this.update();
	}

	/**
	 * Change the locked state
	 */
	public lock(state: boolean) {
		this.locked = state;
	}
}
