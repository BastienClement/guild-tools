import {Constructor, DefaultInjector} from "../utils/DI";
import {PolymerElement, PolymerDynamicTarget} from "./PolymerElement"
import {Application} from "../client/Application";
import {Service} from "../utils/Service";
import {Loader} from "../client/loader/Loader";
import {EventEmitter} from "../utils/EventEmitter";

/**
 * Template of a Polymer element declaration
 */
export type PolymerElementDeclaration = {
	selector: string;
	template?: string;
	extending?: string;
	dependencies?: Constructor<PolymerElement>[];
	behaviors?: any[];
};

/**
 * Declare a Polymer Element
 */
export function Element(decl: PolymerElementDeclaration) {
	return <T extends PolymerElement>(target: Constructor<T>) => {
		Reflect.defineMetadata("polymer:declaration", decl, target);

		/**
		 * Initialize Polymer settings for this element.
		 */
		target.prototype.beforeRegister = function() {
			//noinspection ReservedWordAsName
			this.extends = decl.extending;
			this.is = decl.selector;
			this.behaviors = decl.behaviors || [];
			this.properties = Reflect.getMetadata<any>("polymer:properties", target.prototype) || {};
			this.listeners = Reflect.getMetadata<any>("polymer:listeners", target.prototype) || {};
		};

		/**
		 * Constructor helper
		 */
		target.prototype.createdCallback = function() {
			// Construct a dummy object for obtaining default properties values
			let props = Object.create(null);

			// Perform injections
			// Since this is the first thing done, we can be sure that injected objects are available
			// at any moment inside the object (including Polymer own initialization)
			let inject_bindings = Reflect.getMetadata<InjectionBinding[]>("polymer:injects", target.prototype);
			if (inject_bindings) {
				for (let binding of inject_bindings) {
					props[binding.property] = DefaultInjector.get(binding.ctor);
				}
			}

			// Automatically inject the Application
			props.app = DefaultInjector.get<Application>(Application);

			// Call the original constructor
			// Elements *must not* extends the default TypeScript constructor
			// By default, only properties initialization is performed
			PolymerDynamicTarget(props, () => new target);

			// Since Polymer sometimes calls ready() before returning from
			// callbackCreated, this method ensure that the initialization
			// is complete before calling ready()
			let committed = false;
			const init_commit = () => {
				if (committed) return;
				else committed = true;

				// Define custom sugars
				//noinspection TypeScriptValidateTypes
				Object.defineProperty(this, "node", {
					get: function() { return Polymer.dom(<any> this); }
				});

				//noinspection TypeScriptValidateTypes
				Object.defineProperty(this, "shadow", {
					get: function() { return Polymer.dom(<any> this.root); }
				});

				// Copy injected components on the final object
				for (let key in props) {
					if (this[key] === void 0 && !this.properties[key]) {
						this[key] = props[key];
						delete props[key];
					}
				}

				// If a custom initializer is defined, call it
				// When this function is called, default argument values are not
				// yet available. On the other hand, this function can override them.
				if (this.init) this.init();
			};

			// Hook the ready callback and apply default values obtained
			// from the constructor. Deferring this to the ready event
			// ensure that polymer properly notify listeners.
			let ready = this.ready;
			this.ready = () => {
				// Ensure initialization is done
				init_commit();

				// Copy default values
				for (let key in props) {
					// Ensure no one define the value before
					if (this[key] === void 0) {
						this[key] = props[key];
					}
				}

				// Call the old ready function
				if (ready) ready.call(this);
			};

			// Call polymer constructor
			// Elements *must not* use the created() callback either.
			// Use init() instead
			Polymer.Base.createdCallback.apply(this, arguments);

			// Finalize intialization
			init_commit();
		};

		/**
		 * When the element is attached, register every listener defined using
		 * the @On annotation. Also call the constructor if not yet done.
		 */
		target.prototype.attachedCallback = function() {
			Polymer.Base.attachedCallback.apply(this, arguments);

			// Attach events
			let bindings = Reflect.getMetadata<ElementBindings>("polymer:bindings", target.prototype);
			if (bindings) {
				for (let property in bindings) {
					// Get the EventEmitter object and ensure it is the correct type
					let emitter = <EventEmitter> this[property];
					if (!(emitter instanceof EventEmitter)) continue;

					let mapping = bindings[property];
					for (let event in mapping) {
						let entry = mapping[event];
						let handler: string = <string> (mapping[event] === true ? event : mapping[event]);
						let fn = this[handler];
						if (typeof fn == "function") {
							emitter.on(event, fn, this);
						}
					}
				}
			}

			// Attach to services
			let injects = Reflect.getMetadata<InjectionBinding[]>("polymer:injects", target.prototype);
			if (injects) {
				for (let binding of injects) {
					let injected = this[binding.property];
					if (injected instanceof Service) {
						injected.attachListener(this);
					}
				}
			}
		};

		/**
		 * Unbind from Services and Emitters
		 */
		target.prototype.detachedCallback = function() {
			Polymer.Base.detachedCallback.apply(this, arguments);

			// Detach from services
			let injects = Reflect.getMetadata<InjectionBinding[]>("polymer:injects", target.prototype);
			if (injects) {
				for (let binding of injects) {
					let injected = this[binding.property];
					if (injected instanceof Service) {
						injected.detachListener(this);
					}
				}
			}

			// Detach events
			const bindings = Reflect.getMetadata<ElementBindings>("polymer:bindings", target.prototype);
			if (bindings) {
				for (let property in bindings) {
					// Get the EventEmitter object and ensure it is the correct type
					let emitter = <EventEmitter> this[property];
					if (!(emitter instanceof EventEmitter)) continue;

					let mapping = bindings[property];
					for (let event in mapping) {
						let handler = mapping[event] === true ? event : <string> mapping[event];
						let fn = this[handler];
						if (typeof fn == "function") {
							emitter.off(event, fn, this);
						}
					}
				}
			}
		};

		// There is no attached template, load the element as soon as polymer is loaded
		if (!decl.template) {
			DefaultInjector.get<Loader>(Loader).registerPolymerAutoload(target);
		}
	};
}

/**
 * Declare a Polymer data provider
 */
export function Provider(selector: string) {
	return Element({
		selector: selector,
		extending: "meta"
	});
}

/**
 * Polymer property configuration
 */
type PolymerPropertyConfig = {
	reflect?: boolean;
	readOnly?: boolean;
	notify?: boolean;
	computed?: string;
	observer?: string;
}

/**
 * Declare a Polymer Property
 */
export function Property(config: PolymerPropertyConfig): (t: any, p: string) => void;
export function Property(target: any, property: string): void;
export function Property(target: any, property: string, config: PolymerPropertyConfig): void;
export function Property<T>(target?: any, property?: string, config: PolymerPropertyConfig = {}): any {
	// Called with a config object
	if (!(target instanceof PolymerElement)) {
		return (t: any, p: string) => Property(t, p, target);
	}

	let properties = Reflect.getMetadata<any>("polymer:properties", target) || {};

	// Alias reflect -> reflectToAttribute
	if (config.reflect) {
		(<any> config).reflectToAttribute = true;
	}

	// Transform getter to match Polymer computed property style
	if (config.computed) {
		try {
			const generator = Object.getOwnPropertyDescriptor(target, property).get;
			const updater_key = `_${property.replace(/\W/g, "_")}`;
			target[updater_key] = generator;
			delete target[property];
			config.computed = `${updater_key}(${config.computed.replace(/\s+/g, ",")})`;
		} catch (e) {
			console.error(`Failed to generate computed property '${property}'`);
			throw e;
		}
	}

	// Get type from Typescript annotations
	if (typeof config == "object" && !(<any> config).type) {
		(<any> config).type = Reflect.getMetadata<any>("design:type", target, property);
	}

	properties[property] = config;
	Reflect.defineMetadata("polymer:properties", properties, target);
}

/**
 * Registration of DI-enabled properties
 */
export type InjectionBinding = {
	ctor: Constructor<any>;
	property: string;
}

/**
 * Declare a Polymer dependency injection
 */
export function Inject<T>(target: any, property: string) {
	// Get the field type constructor
	const ctor = Reflect.getMetadata<Constructor<any>>("design:type", target, property);

	// Get the list of injections for this element and insert a new one
	let injects = Reflect.getMetadata<InjectionBinding[]>("polymer:injects", target) || [];
	injects.push({ ctor, property });
	Reflect.defineMetadata("polymer:injects", injects, target);
}

/**
 * Declare a Polymer listeners
 */
export function Listener(...events: string[]) {
	return (target: any, property: string) => {
		let listeners = Reflect.getMetadata<any>("polymer:listeners", target) || {};
		for (let event of events) listeners[event] = property;
		Reflect.defineMetadata("polymer:listeners", listeners, target);
	};
}

//////

type EventMapping = {
	[event: string]: string | boolean;
}

type ElementBindings = {
	[property: string]: EventMapping;
}

type ExtendedMapping = EventMapping | string[] | string;

/**
 * Normalize mapping
 */
function normalize_mapping(mapping: ExtendedMapping): EventMapping {
	if (typeof mapping === "string") {
		return { [mapping]: true };
	} else if (Array.isArray(mapping)) {
		const norm: EventMapping = {};
		mapping.forEach(k => norm[k] = true);
		return norm;
	} else {
		return mapping;
	}
}

/**
 * Declare event biding with externals modules
 */
export function On(m: ExtendedMapping) {
	const mapping = normalize_mapping(m);
	return (target: any, property: string) => {
		let bindings = Reflect.getMetadata<ElementBindings>("polymer:bindings", target) || {};
		bindings[property] = bindings[property] || {};
		for (let key in mapping) {
			bindings[property][key] = mapping[key];
		}
		Reflect.defineMetadata("polymer:bindings", bindings, target);
	};
}

/**
 * Same as @On but automatically adjust for @Notify naming convention
 */
export function Watch(m: ExtendedMapping) {
	const mapping = normalize_mapping(m);
	const adjusted_mapping: { [key: string]: any } = {};
	for (let key in mapping) {
		adjusted_mapping[`${key}-updated`] = mapping[key];
	}
	return On(adjusted_mapping);
}
