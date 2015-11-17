/**
 * Automatically join multiple threads calling the same async procedure
 * during the same microtask. Each of them will end up waiting on the
 * same promise and the function will only be called once.
 */
export function join(target: any, property: string) {
	let fn: () => Promise<any> = target[property];
	let ret_t = Reflect.getMetadata("design:returntype", target, property);

	if (typeof fn != "function" || fn.length !== 0 || (ret_t && ret_t !== Promise))
		throw new TypeError("@join must be applied to a zero-argument async function");

	let results = new WeakMap<any, Promise<any>>();
	let clean = new WeakMap<any, boolean>();

	return {
		value: function() {
			if (results.has(this)) {
				clean.set(this, false);
				return results.get(this);
			}

			let result = Promise.defer<any>();
			results.set(this, result.promise);
			clean.set(this, false);

			const loop = () => {
				if (clean.get(this)) {
					results.delete(this);
					clean.delete(this);
					fn.call(this).then((r: any) => result.resolve(r), (e: any) => result.reject(e));
				} else {
					clean.set(this, true);
					defer(loop);
				}
			};

			defer(loop);
			return result.promise;
		}
	};
}

/**
 * Serialize calls to an async function
 */
export function synchronized(target: any, property: string) {
	let fn: () => Promise<any> = target[property];
	let ret_t = Reflect.getMetadata("design:returntype", target, property);

	if (typeof fn != "function" || (ret_t && ret_t !== Promise))
		throw new TypeError("@synchronized must be applied to an async function");

	let locks = new WeakMap<any, Promise<void>>();

	return {
		value: async function() {
			while (locks.has(this)) await locks.get(this);

			let defer = Promise.defer<void>();
			locks.set(this, defer.promise);

			try {
				return await fn.apply(this, arguments);
			} finally {
				locks.delete(this);
				defer.resolve();
			}
		}
	};
}

// -- Microtask async functions

let defer_queue: [Function, PromiseResolver<any>][] = [];
let defer_toggle = 1;
let defer_node = (function() {
	let observer = new MutationObserver(() => {
		let queue = defer_queue;
		defer_queue = [];
		queue.forEach(entry => {
			let [fn, deferred] = entry;
			try {
				deferred.resolve(fn());
			} catch (e) {
				deferred.reject(e);
			}
		});
	});

	let node = document.createTextNode("");
	observer.observe(node, { characterData: true });

	return node;
})();

/**
 * Execute the given function at the end of the current microtask
 */
export function defer<T>(fn: () => T): Promise<T> {
	let defer = Promise.defer<T>();
	if (defer_queue.push([fn, defer]) == 1) {
		defer_toggle = -defer_toggle
		defer_node.data = <any> defer_toggle;
	}
	return defer.promise;
}

/**
 * Micro-throttle function
 * The annotated function will not be executed more than once
 * during a given microtask
 */
export function throttled(target: any, property: string) {
	let fn = target[property];
	if (typeof fn != "function")
		throw new TypeError("@throttled must be applied to a function");

	let throttled = false;
	let result: any;

	return {
		value: function() {
			if (throttled) return result;
			throttled = true;
			defer(() => throttled = false);
			return result = fn.apply(this, arguments);
		}
	};
}

/**
 * Return an void promise that will be resolved
 * at the end of the current microtask
 */
let microtask_defer: PromiseResolver<void>;
export function microtask(): Promise<void> {
	if (microtask_defer) {
		return microtask_defer.promise;
	} else {
		microtask_defer = Promise.defer<void>();
		defer(() => {
			let deferred = microtask_defer;
			microtask_defer = null;
			deferred.resolve();
		});
		return microtask_defer.promise;
	}
}

// Make a magic global object microtask that return the same promise
Object.defineProperty(window, "microtask", {
	get: function() { return microtask(); }
});
