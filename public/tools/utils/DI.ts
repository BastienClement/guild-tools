// Ensure Reflect.getMetadata is available
if (typeof Reflect !== "object" || typeof Reflect.getMetadata !== "function") {
	throw new Error("Using DI without Reflect.getMetadata() support");
}

/**
 * Loose Interface of a T-constructor
 */
export interface Constructor<T> {
	new (...args: any[]): T
}

/**
 * The DI Injector
 */
export class Injector {
	// Instances cache
	private instances = new Map<Constructor<any>, any>();

	// In-progress modules to prevent infinite initialization loop
	private injecting = new Set<Constructor<any>>();

	/**
	 * Construct a module using dependency injection
	 */
	get<T>(constructor: Constructor<T>): T {
		// Constructor is undefined, probably a circular dependency issue.
		if (!constructor) debugger;

		// Ensure we are creating a DI-enabled component
		if (!Reflect.getMetadata("di:component", constructor)) {
			throw new Error("Attempted to get a non-component by dependency injection");
		}

		// Check is there is already a constructed instance of the requested module
		let instance = this.instances.get(constructor);
		if (instance) return instance;

		// Save the current requested element to prevent infinite loop
		this.injecting.add(constructor);

		// Extract dependencies from metadata
		const deps = Reflect.getMetadata<Constructor<any>[]>("design:paramtypes", constructor) || [];

		// Get dependencies
		const deps_instances = deps.map(dep => {
			if (dep === Injector) return this;
			if (this.injecting.has(dep)) throw new Error(`Circular dependency between ${constructor.name} and ${dep.name}`);
			return this.get(dep);
		});

		// Instantiate the module
		instance = new constructor(...deps_instances);

		// Cache the instance for reuse and remove the module from the injecting set
		this.instances.set(constructor, instance);
		this.injecting.delete(constructor);

		return instance;
	}
}

/**
 * A default, global, injector
 */
export const DefaultInjector = new Injector();

/**
 * Dummy decorator to trigger type metadata
 */
export function Component(target: any) {
	Reflect.defineMetadata("di:component", true, target);
}