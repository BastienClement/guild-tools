/**
 * Promise toolkit
 */

Promise.defer = (function () {
	function PromiseResolver() {
		var self = this;
		this.promise = new Promise(function (res, rej) {
			self.resolve = res,
			self.reject = rej;
		});
	}
	
	return function () {
		return new PromiseResolver();
	}
})();

Promise.onload = function (node) {
	var defer = Promise.defer();
	node.onload = function () { defer.resolve(node); };
	node.onerror = function (e) { defer.reject(e); };
	return defer.promise;
};

Promise.delay = function (delay) {
	var defer = Promise.defer();
	setTimeout(function () { defer.resolve(); }, delay);
	return defer.promise;
};

Promise.require = function (module_name, symbole) {
	return System.import(module_name).then(function (mod) { return symbole ? mod[symbole] : mod });
};

Promise.atLeast = function (duration, promise) {
	return Promise.all([Promise.delay(duration), promise]).then(function (res) {
		return res[1];
	});
};

Promise.prototype.finally = function (finalizer) {
	this.then(finalizer, finalizer);
	return this;
};
