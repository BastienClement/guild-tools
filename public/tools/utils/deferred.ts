export class Deferred<T> {
	public promise: Promise<T>;
	public resolve: (value?: T | Promise<T>) => void;
	public reject: (error?: any) => void;

	constructor() {
		this.promise = new Promise<T>((res, rej) => {
			this.resolve = res;
			this.reject = rej;
		});
	}

	static delay(time: number) {
		const delay = new Deferred<void>();
		setTimeout(() => delay.resolve(), time);
		return delay.promise;
	}

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

	static pipeline<T>(begin: any, jobs: Array<(val: any) => any>): Promise<T> {
		let next: Promise<any> = begin;
		for (let job of jobs) next = (next && next.then) ? next.then(job) : job(next);
		return next;
	}

	static onload<T>(obj: T): Promise<T> {
		const deferred = new Deferred<T>();
		(<any>obj).onload = () => deferred.resolve(obj);
		return deferred.promise;
	}
}
