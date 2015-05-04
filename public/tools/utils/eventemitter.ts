interface TaggedFunction extends Function {
	_once: boolean;
}

class EventEmitter {
	// Flag controlling output of debug informations
	public _eventemitter_debug: boolean = false;

	// Registered event handlers
	private listeners: Map<string, Set<TaggedFunction>> = new Map<string, Set<TaggedFunction>>();

	/**
	 * Register an event handler to be called when the event is dispatched
	 */
	on(event: string, cb: Function): void {
		if (!this.listeners.has(event)) {
			this.listeners.set(event, new Set<TaggedFunction>());
		}

		this.listeners.get(event).add(<TaggedFunction> cb);
	}

	/**
	 * Same as on() but the event handler is called only once
	 */
	once(event: string, cb: Function): void {
		(<TaggedFunction> cb)._once = true;
		this.on(event, cb);
	}

	/**
	 * Remove event handler for a specific event
	 * If `cb` is not given, removes all handlers for a specific event
	 */
	off(event: string, cb?: Function): void {
		// Ensure at least one handler is registered
		if (!this.listeners.has(event)) {
			return;
		}

		if (cb) {
			// Remove a specifc handler
			this.listeners.get(event).delete(<TaggedFunction> cb);
		} else {
			// Remove all handlers
			this.listeners.delete(event);
		}
	}

	/**
	 * Dispatch an event to all registered event handlers
	 */
	protected emit(event: string, ...args: any[]) {
		// Print event emitter debug informations
		if (this._eventemitter_debug) {
			const infos = {
				emitter: this,
				targets: this.listeners.get(event)
			};

			console.info(event, args, infos);
			console.trace();
		}

		// Ensure we have at least one registered handler for this event
		if (!this.listeners.has(event)) return;

		const cb_set = this.listeners.get(event);
		this.listeners.get(event).forEach(cb => {
			cb.apply(null, args);

			// Delete 'once'-handlers
			if (cb._once) {
				cb_set.delete(cb);
			}
		});
	}
}

export default EventEmitter;
