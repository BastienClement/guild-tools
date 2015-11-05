var window = self;
window.document = <any> {
	getElementsByTagName: function(tagName: string) {
		if (tagName === 'script') {
			return [{ dataset: {} }];
		} else if (tagName === 'style') {
			return [];
		} else if (tagName === 'link') {
			return [];
		}
	}
};

module LessWorker {
	importScripts("/assets/javascripts/less.js");

	self.onmessage = function(m) {
		less.render(m.data.args[0])
			.then(function(res) { return res.css; })
			.then(function(css: string) {
				self.postMessage({
					$: "res",
					rid: m.data.rid,
					res: css
				}, void 0);
			});
	};
}
