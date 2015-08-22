import { defer } from "utils/deferred";

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
		const notify = Reflect.getMetadata<{ [prop: string]: boolean }>("eventemitter:notify", this);
		if (notify) {
			const define_property = (prop: string) => {
				let value: any;
				Object.defineProperty(this, prop, {
					get: () => value,
					set: (new_value: any) => {
						const old_value = value;
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
	on(event: string, fn: Function, context: any = this, ...args: any[]): EventEmitter {
		return this.registerHandler(event, fn, false, context, args);
	}

	/**
	 * Bind a listener to the next event. The listener is called only once.
	 */
	once(event: string, fn: Function, context: any = this, ...args: any[]): EventEmitter {
		return this.registerHandler(event, fn, true, context, args);
	}

	/**
	 * Bind events to same-name methods with 'this' bound to the listener object
	 */
	bind(listener: any, ...events: string[]): EventEmitter {
		for (let event of events) {
			this.registerHandler(event, listener[event], false, listener, EmptyArray);
		}
		return this;
	}

	/**
	 * Remove event handler for a specific event
	 * If `cb` is not given, removes all handlers for a specific event
	 */
	off(event: string, fn?: Function, context?: any): EventEmitter {
		const listeners = this.listeners.get(event);

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
	pipe(emitter: EventEmitter, ...filters: string[]) {
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
	unpipe(emitter: EventEmitter) {
		this.pipes.forEach(pipe => {
			if (pipe.emitter === emitter) {
				this.pipes.delete(pipe);
			}
		});
	}

	/**
	 * Dispatch an event to all registered event handlers
	 */
	protected emit(event: string, ...args: any[]): any[] {
		// Pipe to others
		this.pipes.forEach(pipe => {
			if (pipe.filters && pipe.filters.has(event) !== pipe.blacklist) return;
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
	}
}

/**
 * An event emitter with pause() and resume() methods, automatically called
 * when no more event view are listening.
 */
export class PausableEventEmitter extends EventEmitter {
	// Attached objects
	private attachedListeners = new WeakSet<Object>();
	private attachedCount = 0;
	private attachedState = true;

	/**
	 * Attach an object to this emitter
	 * Call resume() if defined and pause() was previously called
	 */
	public attachListener(listener: Object) {
		if (!this.attachedListeners.has(listener)) {
			this.attachedListeners.add(listener);
			this.attachedCount += 1;
			if (!this.attachedState) {
				this.attachedState = true;
				const that: any = this;
				if (that.resume) that.resume();
			}
		}
	}

	/**
	 * Detache an object from this emitter
	 * If the count of attached is now 0, call pause()
	 */
	public detachListener(listener: Object) {
		if (this.attachedListeners.has(listener)) {
			this.attachedListeners.delete(listener);
			this.attachedCount -= 1;
			if (this.attachedCount == 0 && this.attachedState) {
				this.attachedState = false;
				const that: any = this;
				if (that.pause) that.pause();
			}
		}
	}
}

/**
 * Automatically emits event when a property is updated
 */
export function Notify(target: EventEmitter, property: string) {
	const notify = Reflect.getMetadata<{ [prop: string]: boolean }>("eventemitter:notify", target) || {};
	notify[property] = true;
	Reflect.defineMetadata("eventemitter:notify", notify, target);
}
