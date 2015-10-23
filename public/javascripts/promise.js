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

Promise.prototype.finally = function (finalizer) {
	this.then(finalizer, finalizer);
	return this;
};
