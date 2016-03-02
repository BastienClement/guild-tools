import { defer } from "Async";

/**
 * Handler registration object
 */
interface EventHandler {
	fn: Function;
	context: any;
	once: boolean;
	handler: Function;
}

/**
 * Pipe registration object
 */
interface PipedEmitter {
	emitter: EventEmitter;
	filters: Set<string>;
	blacklist: boolean;
}

// An empty array
const EmptyArray: any[] = [];

/**
 * The event emitter base class
 */
export class EventEmitter {
	// Registered event handlers
	private listeners: Map<string, Set<EventHandler>> = new Map<string, Set<EventHandler>>();

	// Registered pipes
	private pipes: Set<PipedEmitter> = new Set<PipedEmitter>();

	/**
	 * Create notification proxy
	 */
	constructor() {
		let notify = Reflect.getMetadata<{ [prop: string]: boolean }>("eventemitter:notify", this);
		if (notify) {
			const define_property = (prop: string) => {
				let value: any;
				Object.defineProperty(this, prop, {
					get: () => value,
					set: (new_value: any) => {
						let old_value = value;
						value = new_value;
						defer(() => this.emit(`${prop}-updated`, value, old_value));
					}
				});
			};

			for (let property in notify) {
				define_property(property);
			}
		}
	}

	/**
	 * Register an event handler to be called when the event is dispatched
	 */
	private registerHandler(event: string, fn: Function, once: boolean, context: any, args: any[]): EventEmitter {
		if (!this.listeners.has(event)) {
			this.listeners.set(event, new Set<EventHandler>());
		}

		this.listeners.get(event).add({
			fn: fn,
			context: context,
			once: once,
			handler: (args.length > 0) ? fn.bind(context, ...args) : fn
		});

		return this;
	}

	/**
	 * Bind a listener to an event
	 */
	public on(event: string, fn: Function, context: any = this, ...args: any[]): EventEmitter {
		return this.registerHandler(event, fn, false, context, args);
	}

	/**
	 * Bind a listener to the next event. The listener is called only once.
	 */
	public once(event: string, fn: Function, context: any = this, ...args: any[]): EventEmitter {
		return this.registerHandler(event, fn, true, context, args);
	}

	/**
	 * Bind event according to a mapping object
	 */
	public bind(listener: any, mapping: { [key: string]: string; }): EventEmitter {
		for (let event in mapping) {
			this.registerHandler(event, listener[mapping[event]], false, listener, EmptyArray);
		}
		return this;
	}

	/**
	 * Remove event handler for a specific event
	 * If `cb` is not given, removes all handlers for a specific event
	 */
	public off(event: string, fn?: Function, context?: any): EventEmitter {
		let listeners = this.listeners.get(event);

		if (listeners) {
			listeners.forEach(listener => {
				if (fn && fn === listener.fn && (!context || context === listener.context)) {
					listeners.delete(listener);
				}
			});
		}

		return this;
	}

	/**
	 * Pipe events to another EventEmitter
	 */
	public pipe(emitter: EventEmitter, ...filters: string[]) {
		let blacklist = false;
		let filters_set: Set<string> = null;

		if (filters.length > 0) {
			filters_set = new Set<string>();
			for (let filter of filters) {
				if (filter == "!") {
					blacklist = true;
				} else {
					filters_set.add(filter);
				}
			}
		}

		this.pipes.add({
			emitter: emitter,
			filters: filters_set,
			blacklist: blacklist
		});
	}

	/**
	 * Remove a piped-to EventEmitter
	 */
	public unpipe(emitter: EventEmitter) {
		this.pipes.forEach(pipe => {
			if (pipe.emitter === emitter) {
				this.pipes.delete(pipe);
			}
		});
	}

	/**
	 * Dispatch an event to all registered event handlers
	 */
	protected emit(event: string, ...args: any[]): Promise<any[]> {
		return defer(() => {
			// Pipe to others
			this.pipes.forEach(pipe => {
				if (pipe.filters && pipe.filters.has(event) === pipe.blacklist) return;
				pipe.emitter.emit(event, ...args);
			});

			// Catch-all listeners
			let listeners = this.listeners.get("*");
			if (listeners) {
				const catchall_args = [event].concat(args);
				listeners.forEach(listener => {
					listener.handler.apply(listener.context, catchall_args);
					if (listener.once) listeners.delete(listener);
				});
			}

			// Results array
			const results: any[] = [];

			// Get registered listeners
			listeners = this.listeners.get(event);
			if (listeners) {
				listeners.forEach(listener => {
					try {
						const result = listener.handler.apply(listener.context, args);
						if (result !== undefined) results.push(result);
						if (listener.once) listeners.delete(listener);
					} catch (e) {
						console.error(e);
					}
				});
			}

			// Return the results array
			return results;
		});
	}
}