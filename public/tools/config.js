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
		"marked": "/assets/javascripts/marked.js",
		"*": "/assets/modules/*.js"
	},
	shim: {
		"pako": { exports: "pako" },
		"encoding": { exports: "Object" },
		"phpbb_hash": { deps: ["cryptojs"], exports: "phpbb_hash" },
		"cryptojs": { exports: "CryptoJS" }
	}
});

var traceur_async = new Promise(function() {});

(function() {
	var fix_sourcemap = localStorage.getItem("traceur.sourcemap.fix") === "1";
	var max_concurrency = 4;
	var worker_static = 4;

	var next_rid = 0;
	var requests = new Map();

	var workers = [];
	var workers_timeout = [];
	var workers_timeout_fn = [];
	var workers_active = 0;
	var free = [];
	var queue = [];

	function flush() {
		while (queue.length > 0 && free.length > 0) {
			var task = queue.pop();
			var wid = free.pop();
			if (wid >= worker_static)
				clearTimeout(workers_timeout[i]);
			workers[wid].postMessage(task);
		}

		if (queue.length > 0 && workers_active < max_concurrency) {
			var i = 0;
			while (workers[i]) i++;
			create_worker(i);
			flush();
		}
	}

	function create_worker(i) {
		var worker = new Worker("/assets/modules/workers/traceur.js");
		workers[i] = worker;
		free.push(i);
		workers_active++;

		worker.onmessage = function(m) {
			var h = requests.get(m.data.rid);
			if (h) {
				requests.delete(m.data.rid);
				h[m.data.index](m.data.value);
				free.push(i);
				flush();
				if (i >= worker_static)
					workers_timeout[i] = setTimeout(workers_timeout_fn[i], 10000);
			}
		};

		if (i >= worker_static) {
			workers_timeout_fn[i] = function() {
				free = free.filter(function(j) {
					return j !== i;
				});
				worker.terminate();
				worker[i] = null;
				workers_timeout[i] = null;
				workers_timeout_fn[i] = null;
				workers_active--;
			};

			workers_timeout[i] = setTimeout(workers_timeout_fn[i], 10000);
		}
	}

	for (var i = 0; i < max_concurrency; i++) {
		create_worker(i);
	}

	traceur_async.then = function(transpile) {
		var rid = next_rid++;
		var name;

		var template = transpile({
			Compiler: function(config) {
				this.compile = function(source, filename) {
					name = filename;
					if (fix_sourcemap) {
						var sourcemap = source.match(/# sourceMappingURL=.*?base64,([^\n]+)/);
						if (sourcemap) config.tsSourceMap = atob(sourcemap[1]);
					} else {
						config.sourceMaps = false;
					}
					queue.push({
						rid: rid,
						config: config,
						source: source,
						filename: filename
					});
					flush();
					return "$placeholder$";
				};
			}
		});

		var task = new Promise(function(res, rej) {
			requests.set(rid, [res, rej]);
		});

		return task.then(function(code) {
			return template.replace("$placeholder$", code);
		});
	};
})();
