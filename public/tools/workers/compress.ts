module compressWorker {
	importScripts("/assets/javascripts/pako.js");

	function deflate(buf: ArrayBuffer) {
		return pako.deflate(new Uint8Array(buf)).buffer;
	}
	
	function inflate(buf: ArrayBuffer) {
		return pako.inflate(new Uint8Array(buf)).buffer;
	}

	self.onmessage = function(m) {
		var res = (m.data.$ == "deflate" ? deflate : inflate)(m.data.args[0]);
		self.postMessage({
			$: "res",
			rid: m.data.rid,
			res: res
		}, <any>[res]);
	};
}    
