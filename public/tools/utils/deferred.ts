class Deferred<T> {
	public promise: Promise<T>;
	public resolve: (value?: T | Promise<T>) => void;
	public reject: (error?: any) => void;

	constructor() {
		this.promise = new Promise<T>((res, rej) => {
			this.resolve = res;
			this.reject = rej;
		});
	}
}

export default Deferred;
