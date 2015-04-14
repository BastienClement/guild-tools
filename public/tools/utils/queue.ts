class Queue<T> {
	private data: T[];
	private capacity: number;
	private start: number;
	private items: number;

	constructor(capacity: number) {
		this.data = new Array<T>(capacity);
		this.capacity = capacity;
		this.start = 0;
		this.items = 0;
	}

	length() {
		return this.items;
	}

	available() {
		return this.capacity - this.items;
	}

	full() {
		return this.items == this.capacity;
	}

	empty() {
		return this.items == 0;
	}

	clear() {
		this.items = 0;
		for (let i = 0; i < this.capacity; ++i) {
			this.data[i] = null;
		}
	}

	enqueue(item: T) {
		if (this.full()) {
			throw new Error("Cannot enqueue() in a full queue");
		}

		this.data[(this.start + (this.items++)) % this.capacity] = item;
	}

	peek(): T {
		if (this.items == 0) return null;
		else return this.data[this.start];
	}

	dequeue(): T {
		if (this.items == 0) return null;
		const item = this.data[this.start];
		this.data[this.start] = null;
		this.items -= 1;
		this.start = (this.start + 1) % this.capacity;
		return item;
	}

	foreach(fn: (item: T) => void) {
		for (let i = 0; i < this.items; i++) {
			const idx = (this.start + i) % this.capacity;
			fn(this.data[idx]);
		}
		return this;
	}

	dequeueWhile(predicate: (item: T) => boolean) {
		while (predicate(this.peek())) {
			this.dequeue();
		}
		return this;
	}
}

export = Queue
