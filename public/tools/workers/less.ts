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

	async function compile(source: string) {
		return (await less.render(source)).css;
	}

	function handler(method: string): (...args: any[]) => Promise<any> {
		switch (method) {
			case "compile": return compile;
		}
	}

	self.onmessage = async(m) => {
		var h = handler(m.data.$);
		if (!h) console.error("Unknown handler", m.data);
        
		self.postMessage({
			$: "res",
			rid: m.data.rid,
			res: await h(...m.data.args)
		}, void 0);
	};
}
