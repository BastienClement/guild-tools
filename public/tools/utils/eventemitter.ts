interface TaggedFunction extends Function {
	_once: boolean;
}

class EventEmitter {
	private listeners: Map<string, Set<TaggedFunction>> = new Map<string, Set<TaggedFunction>>();

	on(event: string, cb: Function) {
		if (!this.listeners.has(event)) {
			this.listeners.set(event, new Set<TaggedFunction>());
		}

		this.listeners.get(event).add(<TaggedFunction> cb);
	}

	once(event: string, cb: Function) {
		(<TaggedFunction> cb)._once = true;
		return this.on(event, cb);
	}

	off(event: string, cb: Function) {
		if (!this.listeners.has(event)) return;
		this.listeners.get(event).delete(<TaggedFunction> cb);
	}

	protected emit(event: string, ...args: any[]) {
		if (!this.listeners.has(event)) return;
		const cb_set = this.listeners.get(event);
		cb_set.forEach(cb => {
			cb.apply(null, args);
			if (cb._once) cb_set.delete(cb);
		});
	}
}

export default EventEmitter;
