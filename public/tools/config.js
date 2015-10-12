System.config({
	transpiler: "traceur_async",
	traceurOptions: {
		experimental: true,
		sourceMaps: "inline"
	},
	paths: {
		"encoding": "/assets/javascripts/encoding.js",
		"phpbb_hash": "/assets/javascripts/phpbb_hash.js",
		"cryptojs": "/assets/javascripts/crypto.js",
		"moment": "/assets/javascripts/moment.js",
		"*": "/assets/modules/*.js"
	},
	shim: {
		"pako": { exports: "pako" },
		"encoding": { exports: "Object" },
		"phpbb_hash": { deps: ["cryptojs"], exports: "phpbb_hash" },
		"cryptojs": { exports: "CryptoJS" }
	}
});

var traceur_async = new Promise(function () { });
(function () {
	var traceur_worker = new Worker("/assets/modules/workers/traceur.js");
	
	var next_rid = 0;
	var requests = new Map();
	
	traceur_worker.onmessage = function (m) {
		var h = requests.get(m.data.rid);
		if (h) {
			requests.delete(m.data.rid);
			h[m.data.index](m.data.value);
		}
	};

	traceur_async.then = function (transpile) {
		var rid = next_rid++;
		var name;

		transpile({
			Compiler: function (config) { 
				this.compile = function (source, filename) {
					name = filename;
					var sourcemap = source.match(/# sourceMappingURL=.*?base64,([^\n]+)/);
					if (sourcemap) config.tsSourceMap = atob(sourcemap[1]);
					traceur_worker.postMessage({
						rid: rid,
						config: config,
						source: source,
						filename: filename
					});
				};
			}
		});
		
		var task = new Promise(function (res, rej) {
			requests.set(rid, [res, rej]);
		});

		return task.then(function (code) {
			return '(function(__moduleName){' + code + '\n})("' + name + '");\n//# sourceURL=' + name + '!transpiled';
		});
	};
})();
