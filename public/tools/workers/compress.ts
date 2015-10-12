module compressWorker {
	importScripts("/assets/javascripts/pako.js");

	function deflate(buf: ArrayBuffer) {
		return pako.deflate(new Uint8Array(buf)).buffer;
	}
	
	function inflate(buf: ArrayBuffer) {
		return pako.inflate(new Uint8Array(buf)).buffer;
	}

	function handler(method: string): any {
		switch (method) {
			case "deflate": return deflate;
			case "inflate": return inflate;
		}
	}

	self.onmessage = function(m) {
		var h = handler(m.data.$);
		if (!h) console.error("Unknown handler", m.data);

		var res: any = h.apply(null, m.data.args);

		self.postMessage({
			$: "res",
			rid: m.data.rid,
			res: res
		}, <any>[res]);
	};
}    
