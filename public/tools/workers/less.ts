"use strict";

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

importScripts("/assets/javascripts/less.js");

async function compile(source: string) {
	return (await less.render(source)).css;
}

function handler(method: string): (...args: any[]) => Promise<any> {
	switch (method) {
		case "compile": return compile;    
	}
}

self.onmessage = async (m) => {
	var h = handler(m.data.$);
	if (!h) console.error("Unknown handler", m.data);
	
	var res = await h(...m.data.args);
	
	var tranfers: any[] = [];
	for (var r of res) {
		if (r instanceof ArrayBuffer || ArrayBuffer.isView(r)) {
			tranfers.push(r);
		}
	}
	
	self.postMessage({
		$: "res",
		rid: m.data.rid,
		res: res
	}, <any> tranfers);
};
