/**
 * Automatically join multiple threads calling the same async procedure.
 * Each of them will end up waiting on the same promise and the function
 * will only be called once.
 */
export function join(target: any, property: string) {
	let fn: () => Promise<any> = target[property];
	let ret_t = Reflect.getMetadata("design:returntype", target, property);
	
	if (typeof fn != "function" || fn.length !== 0 || (ret_t && ret_t !== Promise))
		throw new TypeError("@join must be applied to a zero-argument async function");

	let results = new WeakMap<any, Promise<any>>();
    
	return {
		value: function() {
			if (results.has(this)) return results.get(this);
			let result: Promise<any> = fn.call(this);
			results.set(this, result);
			return result.finally(() => results.delete(this));
		}
	}    
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
	}    
}
