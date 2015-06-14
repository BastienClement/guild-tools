export class Queue<T> {
	// The data store array
	private queue: T[] = [];

	// The number of free slots in front of actual data
	private offset: number = 0;

	/**
	 * Return the amount of item inside the queue
	 */
	length() {
		return this.queue.length - this.offset;
	}

	/**
	 * Check if the queue is empty
	 */
	empty() {
		return this.queue.length == 0;
	}

	/**
	 * Empty the queue
	 */
	clear() {
		this.queue.length = 0;
	}

	/**
	 * Add a new item at the end of the queue
	 */
	enqueue(item: T) {
		this.queue.push(item);
	}

	/**
	 * Return the first element of the queue whithout removing it
	 */
	peek(): T {
		if (this.empty()) return null;
		return this.queue[this.offset];
	}

	/**
	 * Return and remove the first element of the queue and free memory if necessary
	 */
	dequeue(): T {
		if (this.empty()) return null;

		// Grab the item and free the array cell
		const item = this.queue[this.offset];
		this.queue[this.offset] = null;

		// Half of the queue is empty, remove the free space
		if (++this.offset * 2 >= this.queue.length) {
			this.queue = this.queue.slice(this.offset);
			this.offset = 0;
		}

		return item;
	}

	/**
	 * Replace the first item of the queue
	 */
	update(item: T) {
		if (this.empty()) return;
		this.queue[this.offset] = item;
	}

	/**
	 * Execute a function over all the items in the queue
	 */
	foreach(fn: (item: T) => void) {
		const length = this.queue.length;
		for (let i = this.offset; i < length; i++) {
			fn(this.queue[i]);
		}
		return this;
	}
	
	/**
	 * Check if the element is present inside the queue
	 */
	contains(item: T): boolean {
		const length = this.queue.length;
		for (let i = this.offset; i < length; i++) {
			if (this.queue[i] === item) return true;
		}
		return false;
	}
}
