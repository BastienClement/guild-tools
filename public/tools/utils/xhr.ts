import { Deferred } from "utils/deferred";

export function XHR<T>(url: string, type: string): Promise<T> {
	const deferred = new Deferred<T>();

	const xhr = new XMLHttpRequest();
	xhr.open("GET", url, true);
	xhr.responseType = type;

	xhr.onload = function() {
		if (this.status == 200) {
			deferred.resolve(this.response);
		} else {
			deferred.reject(new Error());
		}
	};

	xhr.send();
	return deferred.promise;
}

export function XHRText(url: string): Promise<string> {
	return XHR<string>(url, "text");
}
