/**
 * Represent a not-yet-available value
 */
export class Deferred<T> {
	public promise: Promise<T>;

	public resolve: (value?: T | Promise<T>) => void;
	public reject: (error?: any) => void;

	private _resolved = false;
	get resolved() { return this._resolved; }

	constructor() {
		this.promise = new Promise<T>((res, rej) => {
			this.resolve = (value: T | Promise<T>) => {
				this._resolved = true;
				res(value);
			};

			this.reject = (reason: any) => {
				this._resolved = true;
				rej(reason);
			};
		});
	}

	//
	// --- Static utilities ---
	//

	/**
	 * Create a promise that will be resolved after a delay (ms)
	 */
	static delay(time: number): Promise<void> {
		const delay = new Deferred<void>();
		setTimeout(() => delay.resolve(), time);
		return delay.promise;
	}

	/**
	 * Create a promise that will be resolved when the given object onload() method is called
	 */
	static onload<T extends { onload: Function; onerror?: Function }>(obj: T): Promise<T> {
		const deferred = new Deferred<T>();
		obj.onload = () => deferred.resolve(obj);
		obj.onerror = (e: any) => deferred.reject(e);
		return deferred.promise;
	}

	/**
	 * Attach a finalizer function to a promise completion and return the same promise
	 */
	static finally<T>(promise: Promise<T>, finalizer: Function): Promise<T> {
		promise.then(() => finalizer(), () => finalizer());
		return promise;
	}

	/**
	 * Promise interface to Require.js
	 */
	static require<T>(module_name: string, symbole?: string): Promise<T> {
		return System.import<any>(module_name).then(mod => symbole ? mod[symbole] : mod);
	}
}

// ------------
// High-performance async callback

const defer_queue: [Function, Deferred<any>][] = [];

const defer_node = (function() {
	const observer = new MutationObserver(() => {
		let queue = defer_queue.slice();
		defer_queue.length = 0;
		queue.forEach(entry => {
			let [fn, defer] = entry;
			try {
				defer.resolve(fn());
			} catch (e) {
				defer.reject(e);
			}
		});
	});

	const node = document.createTextNode("");
    observer.observe(node, { characterData: true });

	return node;
})();

let defer_toggle = 1;

/**
 * Execute the given function at the end of the current microtask
 */
export function defer<T>(fn: () => T): Promise<T> {
	let defer = new Deferred<T>();
	if (defer_queue.push([fn, defer]) == 1) {
		defer_toggle = -defer_toggle
		defer_node.data = <any> defer_toggle;
	}
	return defer.promise;
}

/**
 * Micro-throttle function
 */
export function throttle(target: any, property: string) {
	let throttled = false;
	let fn = target[property];
	return {
		value: function() {
			if (throttled) return
			throttled = true;
			defer(() => throttled = false);
			fn.apply(this, arguments);
		}
	};    
}