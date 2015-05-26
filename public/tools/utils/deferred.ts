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
	static parallel(jobs: Promise<any>[]): Promise<any[]> {
		const done = new Deferred<any>();

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
	static onload<T extends { onload: Function }>(obj: T): Promise<T> {
		const deferred = new Deferred<T>();
		obj.onload = () => deferred.resolve(obj);
		return deferred.promise;
	}
}
