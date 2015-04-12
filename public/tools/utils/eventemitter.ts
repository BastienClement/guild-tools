class EventEmitter {
	private listeners = new Map<string, Function[]>();

	on<T extends Function>(event: string, cb: T) {
		if (!this.listeners.has(event)) {
			this.listeners.set(event, []);
		}

		this.listeners.get(event).push(cb);
	}

	off(event: string, cb: Function) {
		if (!this.listeners.has(event)) return;
		this.listeners.set(event, this.listeners.get(event).filter(f => f != cb));
	}

	emit(event: string, ...args: any[]) {
		if (!this.listeners.has(event)) return;
		this.listeners.get(event).forEach((cb: Function) => cb.apply(null, args));
	}
}

export = EventEmitter;
