import { Component } from "utils/di";
import { Deferred } from "utils/deferred";

/**
 * The Response object returned by fetch()
 */
interface FetchResponse {
	text(): Promise<string>;
}

/**
 * Alias to the fetch function for type-safety purpose
 */
const Fetch: (url: string) => Promise<FetchResponse> = (<any>window).fetch;

/**
 * Resource loading service
 */
@Component
export class ResourceLoader {
	/**
	 * Previous requests cache
	 */
	private cache: Map<string, Promise<string>> = new Map<string, any>();
	
	/**
	 * Fetch a server-side resource as text
	 */
	fetch(url: string, cache: boolean = true): Promise<string> {
		// Check the cache for the resource
		if (this.cache.has(url)) return this.cache.get(url);
		
		// Cache and return helper
		const cache_and_return = (data: Promise<string>) => {
			if (cache) {
				this.cache.set(url, data);
				// Remove from cache if fetch failed
				data.then(null, () => this.cache.delete(url));
			}
			return data;
		};
		
		// If the Fetch API is available, use it
		if (Fetch) return cache_and_return(Fetch(url).then((res) => res.text()))
		
		// Fallback to XHR
		const defer = new Deferred<string>();
		const xhr = new XMLHttpRequest();
		
		xhr.open("GET", url, true);
		xhr.responseType = "text";
		
		xhr.onload = function() {
			if (this.status == 200) {
				defer.resolve(this.response);
			} else {
				defer.reject(new Error());
			}
		};
		
		xhr.onerror = (e) => defer.reject(e);
		
		xhr.send();
		return cache_and_return(defer.promise);
	}
}
