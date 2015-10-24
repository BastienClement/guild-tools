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
}

Promise.prototype.finally = function (finalizer) {
	this.then(finalizer, finalizer);
	return this;
};
