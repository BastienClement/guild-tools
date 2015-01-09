importScripts("/assets/javascripts/pako.js");
importScripts("/assets/javascripts/string-view.js");

self.onmessage = function(msg) {
	var data = msg.data;
	var payload;

	switch (data.$) {
		case "deflate":
			payload = data.payload;
			var comp_mode, comp_buf;

			if (payload.length < 250) {
				comp_mode = 0x00;
				comp_buf = new StringView(payload).rawData;
			} else {
				comp_mode = 0x01;
				comp_buf = pako.deflate(payload);
			}

			var buf = new Uint8Array(comp_buf.length + 1);
			buf[0] = comp_mode;
			buf.set(comp_buf, 1);

			self.postMessage({ $: "deflate-data", buf: buf });
			break;

		case "inflate":
			var compByte = new Uint8Array(data.buf, 0, 1);

			if (compByte[0]) {
				payload = pako.inflate(new Uint8Array(data.buf, 1), { to: "string" });
			} else {
				payload = new StringView(data.buf, "UTF-8", 1).toString();
			}

			self.postMessage({ $: "inflate-data", payload: payload });
			break;
	}
};
