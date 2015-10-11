import { Deferred } from "utils/deferred";

let instances = new Map<string, ServiceWorker>();

export class ServiceWorker {
	private worker: Worker;
	
	private next_rid = 0;
	private requests = new Map<number, Deferred<any>>();
	
	constructor(path: string) {
		if (instances.has(path)) {
			return instances.get(path);
		} else {
			instances.set(path, this);
		}
		
		this.worker = new Worker(path);
		
		this.worker.onmessage = (m) => {
			if (m.data.$ === "res") {
				let rid = m.data.rid;
				let defer = this.requests.get(rid);
				if (defer) {
					defer.resolve(m.data.res);
					this.requests.delete(rid);
				} else {
					console.error("Undefined request", m.data);
				}
			} else {
				console.error("Undefined message", m.data);
			}    
		};
		
		this.worker.onerror = (e) => console.error(e);
	}
	
	public request<T>(method: string, ...args: any[]): Promise<T> {
		let defer = new Deferred<T>();
		let rid = this.next_rid++;
		this.requests.set(rid, defer);
		
		this.worker.postMessage({
			$: method,
			rid: rid,
			args: args
		});
		
		return defer.promise;
	}
}
