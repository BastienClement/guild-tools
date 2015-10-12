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

	function compile(source: string) {
		return less.render(source).then(function(res) { return res.css; });
	}

	function handler(method: string): (...args: any[]) => Promise<any> {
		switch (method) {
			case "compile": return compile;
		}
	}

	self.onmessage = function(m) {
		var h = handler(m.data.$);
		if (!h) console.error("Unknown handler", m.data);
        
		h.apply(null, m.data.args).then(function(css: string) {
			self.postMessage({
				$: "res",
				rid: m.data.rid,
				res: css 
			}, void 0);
		});
	};
}
