/**
 * Interface of the function returned by Deferred#lazy()
 */
export interface LazyThen<T> {
	<U>(fn: (value: T) => U | Promise<U>): Promise<U>;
	(): Promise<T>;
}

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
	 * Create a resolved promise
	 */
	static resolved<T>(value: T): Promise<T> {
		const d = new Deferred<T>();
		d.resolve(value);
		return d.promise;
	}

	/**
	 * Create a rejected promise
	 */
	static rejected<T>(err: Error): Promise<T> {
		const d = new Deferred<any>();
		d.reject(err);
		return d.promise;
	}

	/**
	 * Create a promise that will be resolved after a delay (ms)
	 */
	static delay(time: number): Promise<void> {
		const delay = new Deferred<void>();
		setTimeout(() => delay.resolve(), time);
		return delay.promise;
	}

	/**
	 * Execute multiple promise-returning function in parallel
	 */
	static all<T>(jobs: Promise<T>[]): Promise<T[]> {
		// Short circuit on empty array
		if (jobs.length < 1) return Deferred.resolved([]);

		const done = new Deferred<any[]>();

		const results = new Array(jobs.length);
		let left = jobs.length;

		function result(idx: number) {
			return (res: any) => {
				results[idx] = res;
				if (--left == 0) {
					done.resolve(results);
				}
			};
		}

		for (let i = 0; i < jobs.length; ++i) {
			jobs[i].then(result(i), (e) => done.reject(e));
		}

		return done.promise;
	}

	/**
	 * Execute multiple promise-returning function sequentially
	 */
	static pipeline<T>(begin: any, jobs: Array<(val: any) => any>): Promise<T> {
		let next: Promise<any> = begin;
		for (let job of jobs) next = (next && next.then) ? next.then(job) : job(next);
		return next;
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

	/**
	 * Construct a lazy promise
	 */
	/*static lazy<T>(init: () => T | Promise<T>): LazyThen<T> {
		let promise: Promise<T> = null;
		return (fn?: (value: T) => any) => {
			if (!promise) {
				const value = init();
				promise = (value instanceof Promise) ? value : Deferred.resolved(promise);
			}
			return fn ? promise.then(fn) : promise;
		};
	}*/
}

// ------------
// High-performance async callback

const defer_queue: Function[] = [];

const defer_node = (function() {
	const observer = new MutationObserver(() => {
		defer_queue.forEach(fn => fn());
		defer_queue.length = 0;
	});

	const node = document.createTextNode("");
    observer.observe(node, { characterData: true });

	return node;
})();

let defer_toggle = 1;

/**
 * Execute the given function at the end of the current microtask
 */
export function defer(fn: () => void) {
	if (defer_queue.push(fn) == 1) {
		defer_toggle = -defer_toggle
		defer_node.data = <any> defer_toggle;
	}
}
