var window = self;
window.document = {
	getElementsByTagName: function(tagName) {
		if (tagName === 'script') {
			return [{ dataset: {} }];
		} else if (tagName === 'style') {
			return [];
		} else if (tagName === 'link') {
			return [];
		}
		return null;
	}
};

importScripts("/assets/javascripts/less.js");

self.onmessage = function(m) {
	less.render(m.data.args[0])
		.then(function(res) { return res.css; })
		.then(function(css) {
			self.postMessage({
				$: "res",
				rid: m.data.rid,
				res: css
			});
		});
};
