"use strict";
module compressWorker {

	importScripts("/assets/javascripts/pako.js");

	async function deflate(buf: ArrayBuffer) {
		return pako.deflate(new Uint8Array(buf)).buffer;
	}
	
	async function inflate(buf: ArrayBuffer) {
		return pako.inflate(new Uint8Array(buf)).buffer;
	}

	function handler(method: string): (...args: any[]) => Promise<any> {
		switch (method) {
			case "deflate": return deflate;
			case "inflate": return inflate;
		}
	}

	self.onmessage = async(m) => {
		var h = handler(m.data.$);
		if (!h) console.error("Unknown handler", m.data);

		var res = await h(...m.data.args);

		self.postMessage({
			$: "res",
			rid: m.data.rid,
			res: res
		}, <any>[res]);
	};
}    
