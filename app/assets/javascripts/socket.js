importScripts("/assets/javascripts/pako.js");

var socket_url = null;
var settings = {};
var ws = null;
var queue = [];
var rev = null;
var tries = 0;

self.onerror = function(e) {
	console.error(e);
};

(function() {
	var xhr = new XMLHttpRequest();
	xhr.open("GET", "/api/socket_url", false);

	xhr.onload = function () {
		if (this.status == 200) {
			socket_url = this.responseText;
		} else {
			socket_url = document.location.origin.replace(/^http/, "ws") + "/socket";
		}
	};

	xhr.send();
})();

function ws_reconnect() {
	self.postMessage({ $: "reconnect" });

	ws = null;
	setTimeout(function() {
		ws_connect(function() {
			if (tries > 5) {
				return self.postMessage({ $: "error", args: [
					"You have been disconnected from the server",
					"The connection to the server could not be restored. Please refresh the page or restart the client."
				]});
			}
			ws_reconnect();
		});
	}, (Math.pow(2, tries++) + Math.random()) * 1000);
}

function ws_read(frame, cb) {
	var data, msg;

	if (settings.enable_compression) {
		data = pako.inflate(new Uint8Array(frame.data), { to: 'string' });
	} else {
		data = frame.data;
	}

	try {
		msg = JSON.parse(data);
	} catch (e) {
		return cb(e, data);
	}

	return msg;
}

function ws_connect(err) {
	ws = new WebSocket(socket_url + (settings.enable_compression ? "_z" : ""));
	if (settings.enable_compression) ws.binaryType = "arraybuffer";

	function cb(info) {
		if (err) return err(info);
		err = null;
	}

	ws.onclose = function(e) {
		ws = null;
		return cb(e);
	};

	ws.onmessage = function(frame) {
		var msg = ws_read(frame, function(e, data) {
			console.error(e, data);
			cb({ reason: "invalid handshake" });
			ws.close();
		});

		if (msg.service !== "GuildTools" || msg.protocol !== "GTP2" || msg.version !== "5.0") {
			return self.postMessage({ $: "error", args: [
				"Unable to validate game version",
				"This may be caused by file corruption or the interference of another program.",
				"Error #113"
			]});
		}

		if (rev && rev !== msg.rev) {
			self.postMessage({ $: "serv-update" });
		} else {
			self.postMessage({ $: "serv-rev", rev: rev });
		}

		ws.onclose = ws_reconnect;
		ws.onmessage = function(frame) {
			var msg = ws_read(frame, function(e, data) {
				console.error(e);
				self.postMessage({ $: "bugsack", payload: e });
				ws.close();
			});

			self.postMessage({ $: "message", msg: msg });
		};

		self.postMessage({ $: "auth" });
	};
}

self.onmessage = function(e) {
	var data = e.data;
	switch(data.$) {
		case "set":
			settings = data.settings;
			break;

		case "connect":
			ws_connect(function(e) {
				self.postMessage({
					$: "connect-error",
					err: e
				});
			});
			break;

		case "send":
			var msg = data.msg;
			if (ws) {
				if (settings.enable_compression) {
					ws.send(pako.deflate(JSON.stringify(msg)));
				} else {
					ws.send(JSON.stringify(msg));
				}
			} else {
				queue.push(msg);
			}
			break;
	}
};
