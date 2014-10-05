var $ = {};
(function() {

var ready = false;
var dead  = false;

$.online = [];
$.onlineMap = {};

_(function() { ready = true; });

$.init = function() {
	if ($$) $$.init(dead);
	if (dead) return;
	
	$.wsConnect(function(e) {
		$.error(
			"Unable to connect to the server",
			"Please check your internet connection and your firewall configuration (port 2292) and try again.",
			e.reason || "unknown error"
		);
	});
};

$.anonSessId = function() {
	var s = localStorage.getItem("session.token");
	return s ? s.slice(0, 20) : "";
};

var failLoad = setTimeout(function() {
	$.error("Unknown error", "An unknown error prevented the application from loading.");
}, 15000);

$.load = function() {
	clearTimeout(failLoad);
	_("#loading-css").remove();
	_("#loading-cover").fadeOut(200, function() {
		_("body").removeClass("loading").addClass("loaded");
	});
};

var error = false;
$.error = function(title, text, infos) {
	if (error) return;
	error = true;
	dead  = true;
	
	ga('send', 'exception', {
		'exDescription': title + " - " + infos,
		'exFatal': true
	});
	
	if (ready) {
		_("#loading-error-title").text(title);
		_("#loading-error-text").text(text);
		_("#loading-error-infos").text(infos || "");
		_("#loading-error").css({ display: "block" });
		if ($$) $$.error();
	} else {
		_(function() {
			error = false;
			$.error(title, text, infos);
		});
	}
};

var features = ["websockets", "flexbox", "localstorage", "history", "contenteditable"];
for (var i = 0; i < features.length; ++i) {
	if (!Modernizr[features[i]]) {
		$.error(
			"Unsupported browser",
			"Some technologies required by GuildTools are not supported by your browser.",
			"unsupported_" + features[i]
		);
		break;
	}
}

$.syncOnlines = function() {
	$.exec("chat:onlines", function(err, data) {
		if (err) return;
		$.online = data;
		$.onlineMap = {};
		$.online.forEach(function(player) {
			$.onlineMap[player.id] = player;
		});
	});
};

//
// Web socket
//
(function() {

var ws = null;
var tries = 0;
var calls = [];
var inflight = 0;
var queue = [];
var sockid = null;
var init_done = false;
var reason = null;

$.getInflight = function() {
	return inflight;
};

$.wsAPI = {
	"ping": function(cb) {
		cb();
	},
	
	"error": function(text) {
		if (typeof GuildToolsScope === "object") {
			GuildToolsScope.error(text);
		}
	},
	
	"chat:onlines:update": function(msg) {
		var data = msg.data;
		switch (msg.type) {
			case "online":
				if ($.onlineMap[data.id]) {
					var player = $.onlineMap[data.id];
					for (var key in data) {
						player[key] = data[key];
					}
				} else {
					$.online.push(data);
					$.onlineMap[data.id] = data;
				}
				break;
				
			case "offline":
				$.online = $.online.filter(function(player) {
					return player.id !== data;
				});
				delete $.onlineMap[data];
				break;
		}
	},
	
	"event:dispatch": function(event) {
		GuildToolsScope.dispatchEvent(event.name, event.arg);
	}
};

$.exec = function(cmd, arg, cb) {
	if (typeof arg === "function") {
		cb = arg;
		arg = null;
	}
	
	var id = cb ? calls.push(cb) - 1 : null;
	
	$.wsSend({
		"$": cmd,
		"&": arg,
		"#": id
	});
};

$.call = function(cmd, arg, cb) {
	if (typeof arg === "function") {
		cb = arg;
		arg = null;
	}
	
	var wrapped_cb = function() {
		setTimeout(function() {
			--inflight;
		}, 100);
		if (cb) cb.apply(null, arguments);
	};

	++inflight;
	//console.log("START", args);
	if (!Pace.running) Pace.restart();
	
	$.exec(cmd, arg, wrapped_cb);
};

$.wsTest = function() { ws.close(); };

$.wsConnect = function(err) {
	ws = new WebSocket(document.location.origin.replace(/^http/, "ws") + "/socket");
	state = 0;
	
	function cb(info) {
		if (err) return err(info);
		err = null;
	}
	
	ws.onclose = function(e) {
		ws = null;
		return cb(e);
	};
	
	ws.onmessage = function(msg) {
		try {
			msg = JSON.parse(msg.data);
		} catch (e) {
			cb({ reason: "invalid handshake" });
			ws.close();
			return;
		}
		
		if (msg.service !== "GuildTools" || msg.protocol !== "GTP2" || msg.version !== "5.0") {
			return $.error(
				"Unable to validate game version",
				"This may be caused by file corruption or the interference of another program.",
				"Error #113"
			);
		}
		
		ws.onclose = $.wsReconnect;
		ws.onmessage = function(msg) {
			try {
				msg = JSON.parse(msg.data);
			} catch (e) {
				console.error(e);
				ws.close();
				return;
			}
			
			var cmd = msg.$;
			var id  = msg["#"];
			var arg = msg["&"];
			var handler = typeof id === "number" ? calls[id] : null;
			
			switch (cmd) {
				case "ack":
				case "res":
					if (typeof handler !== "function") return;
					try {
						handler.call(null, null, arg);
						if (GuildToolsScope) GuildToolsScope.safeApply();
					} catch (e) {
						console.error(e);
					}
					
					delete calls[msg.results];
					break;
					
				case "nok":
				case "err":
					if (typeof handler !== "function") return;
					try {
						handler.call(null, arg);
						if (GuildToolsScope) GuildToolsScope.safeApply();
					} catch (e) {
						console.error(e);
					}
					
					delete calls[msg.results];
					break;
				
				case "close":
					reason = arg;
					break;
				
				default:
					var method = $.wsAPI[cmd];
					if (!method) return;
					
					try {
						method.call(null, arg);
						if (GuildToolsScope) GuildToolsScope.safeApply();
					} catch (e) {
						console.error(e);
					}
			}
		};
		
		$.wsAuth();
	};
};

$.wsAuth = function(redirect) {
	$.call("auth", { session: localStorage.getItem("session.token"), socket: sockid }, function(err, res) {
		if (err) return;
		if (res.resume) {
			for (var i = 0; i < queue.length; ++i) {
				$.wsSend(queue[i]);
			}
			if($.user) ga('send', 'event', 'session', 'resume', $.anonSessId());
			queue = [];
		} else if (sockid !== null) {
			sockid = res.socket;
			$.user = res.user;
			if($.user) {
				$.syncOnlines();
				ga('set', 'userId', $.user.id);
				ga('send', 'event', 'session', 'resync', $.anonSessId());
			}
			GuildToolsScope.syncContext();
		} else {
			sockid = res.socket;
			$.user = res.user;
			if($.user) {
				$.syncOnlines();
				ga('set', 'userId', $.user.id);
				ga('send', 'event', 'session', 'continue', $.anonSessId());
			}
		}
		
		if(!init_done) {
			init_done = true;
			angular.bootstrap(document, ["GuildTools"]);
			_("body").append("<div id='loading-done'></div>");
		}
		
		GuildToolsScope.popupErrorText = null;
		GuildToolsScope.safeApply(function() {
			if (!$.user) {
				GuildToolsLocation.path("/login").replace();
			} else if (!$.user.ready) {
				GuildToolsLocation.path("/welcome").replace();
			} else if (redirect) {
				GuildToolsLocation.path("/dashboard").replace();
			}
		});
	});
};

$.wsSend = function(m) {
	if (ws) {
		ws.send(JSON.stringify(m));
	} else {
		queue.push(m);
	}
};

$.wsReconnect = function(e) {
	if (dead) return;
	
	if (reason !== null) {
		$.error(
			"You have been disconnected from the server",
			"The server has closed the connection in response to an error.",
			reason
		);
		reason = null;
		return;
	}
	
	GuildToolsScope.popupErrorText = "Reconnecting...";
	GuildToolsScope.safeApply();
	
	ws = null;
	setTimeout(function() {
		$.wsConnect(function() {
			if (tries > 5) {
				return $.error(
					"You have been disconnected from the server",
					"The connection to the server could not be restored. Please refresh the page or restart the client."
				);
			}
			$.wsReconnect();
		});
	}, (Math.pow(2, tries++) + Math.random()) * 1000);
};

})();

//
// Battle.net API
//
(function() {

var queue = [];
var busy = false;
var head = document.getElementsByTagName("head")[0];
var script = null;
var timeout = null;

function drain() {
	busy = true;
	var src = queue[0][0];
	
	if (script) {
		head.removeChild(script);
		script = null;
	}
	
	var separator = (src.indexOf("?") === -1) ? "?" : "&";
	
	script = document.createElement("script");
	
	script.onerror = function() {
		$.bnHandler(null);
	};
	
	script.onload = function() {
		to = setTimeout(function() {
			$.bnHandler(null);
		}, 500);
	};
	
	script.src = "//eu.battle.net/api/wow/" + src + separator + "jsonp=$.bnHandler";
	head.appendChild(script);
}

$.bnQuery = function(src, cb) {
	queue.push([src, cb]);
	if (!Pace.running) Pace.restart();
	if (!busy) drain();
};

$.bnHandler = function(data) {
	clearTimeout(timeout);
	var call = queue.shift();
	try { call[1](data); } catch (e) {}
	GuildToolsScope.safeApply();
	if (queue.length) {
		drain();
	} else {
		busy = false;
	}
};

$.bnInflight = function() {
	return queue.length;
};

})();

})();