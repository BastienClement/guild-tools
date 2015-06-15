// Ensure Reflect.getMetadata is available
if (typeof Reflect !== "object" || typeof Reflect.getMetadata !== "function")
	throw new Error("Using DI without Reflect.getMetadata() support");	

/**
 * Iterface of a T-constructor
 */
export interface Constructor<T> extends Function {
	new (): T;
}

/**
 * Interface of a locally declared Constructor
 */
export interface LocalConstructor<T> extends Function {}

/**
 * The DI Injector
 */
export class Injector {
	// Instances cache
	private instances: Map<Function, any> = new Map<Function, any>();
	
	// In-progress modules to prevent infinite initialization loop
	private injecting: Set<Function> = new Set<Function>();
	
	/**
	 * Construct a module using dependency injection
	 */
	get<T>(constructor: Constructor<T> | LocalConstructor<T>): T {
		// Check is there is already a constructed instance of the requested module
		let instance = this.instances.get(constructor);
		if (instance) return instance;
		
		// Save the current requested element to prevent infinite loop
		this.injecting.add(constructor);
		
		// Extract dependencies from metadata
		const deps: Constructor<any>[] = Reflect.getMetadata("design:paramtypes", constructor) || [];
		
		// Get dependencies
		const deps_instances = deps.map(dep => {
			if (dep === Injector) return this;
			if (this.injecting.has(dep)) throw new Error(`Circular dependency between ${constructor.name} and ${dep.name}`);
			return this.get(dep);
		})
		
		// Instantiate the module
		instance = Object.create(constructor.prototype);
		instance = constructor.apply(instance, deps_instances) || instance;
		
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
export function Component() {}
