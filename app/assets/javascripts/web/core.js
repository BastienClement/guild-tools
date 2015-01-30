$ = {};

(function() {
	// Error catcher
	function bugsack_send(e) {
		if (typeof e != "object") e = {};

		var report = {
			user: ($.user && $.user.id) || null,
			rev: $.rev || null,
			msg: e.message,
			stack: e.stack,
			nav: navigator.userAgent
		};

		var xhr = new XMLHttpRequest();
		xhr.open("POST", "/api/bugsack", true);
		xhr.setRequestHeader("Content-Type", "application/json");
		xhr.send(JSON.stringify(report));
	}

	window.addEventListener("error", function(e) {
		bugsack_send(e.error);
	});

	var ready = false;
	var dead = false;

	$.online = [];
	$.roster = { users: [], chars: [] };
	$.rev = null;
	$.shoutbox = [];

	_(function() {
		ready = true;
	});

	_(document).bind("contextmenu", function() { return false; });

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
		dead = true;

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
		$.exec("roster:load", function(err, data) {
			if (err) return;
			$.roster.users = [];
			data.users.forEach(function(user) { $.roster.users[user.id] = user; });
			$.roster.chars = [];
			data.chars.forEach(function(char) { $.roster.chars[char.id] = char; });
			$.roster.trigger();
		});

		$.exec("chat:sync", function(err, data) {
			if (err) return;
			$.online = data.onlines;
			$.onlineMap = {};
			$.online.forEach(function(player) {
				$.onlineMap[player.id] = player;
			});

			$.shoutbox = data.shoutbox;
			$.shoutbox.reverse();
		});
	};

//
// Roster
//
	(function() {
		var unknown_user_queries = {};
		var unknown_char_queries = {};

		var idx_chars_by_user = [];
		var idx_main_for_user = [];

		$.roster.isCrossRealm = function(char) {
			if (char._cross_realm) return true;
			var server = typeof char == "string" ? char : char.server;
			if (server) {
				var cross_realm = server != "sargeras" && server != "nerzhul" && server != "garona";
				if (cross_realm && typeof char == "object") {
					char._cross_realm = true;
				}
				return cross_realm;
			}
			return false;
		};

		$.roster.user = function(id) {
			id = Number(id);
			var user = $.roster.users[id];

			if (!user) {
				user = $.roster.users[id] = {
					id: id,
					name: "User#" + id,
					color: "64B4FF",
					ilvl: 0,
					unknown: true
				};
			}

			if (user.unknown && (!unknown_user_queries[id] || (Date.now() - unknown_user_queries[id]) > 60000) && !isNaN(id)) {
				unknown_user_queries[id] = Date.now();
				$.exec("roster:user", { id: id }, function(err, data) {
					if (err || !data.user) return;
					$.roster.users[data.user.id] = data.user;
					if (data.outofroster) data.user.outofroster = true;
					data.chars.forEach(function(char) {
						$.roster.chars[char.id] = char;
					});
					$.roster.trigger();
				});
			}

			return user;
		};

		$.roster.char = function(id) {
			id = Number(id);
			var char = $.roster.chars[id];

			if (!char) {
				char = $.roster.chars[id] = {
					id: id,
					name: "Char#" + id,
					"class": 99,
					ilvl: 0,
					unknown: true
				};
			}

			if (char.unknown && (!unknown_char_queries[id] || (Date.now() - unknown_char_queries[id]) > 60000) && !isNaN(id)) {
				unknown_char_queries[id] = Date.now();
				$.exec("roster:char", { id: id }, function(err, char) {
					if (err || !char) return;
					$.roster.chars[id] = char;
					$.roster.trigger();
				});
			}

			return char;
		};

		$.roster.buildIndexes = function() {
			idx_chars_by_user = [];
			$.roster.chars.forEach(function(char) {
				if (!idx_chars_by_user[char.owner]) {
					idx_chars_by_user[char.owner] = [];
				}
				idx_chars_by_user[char.owner].push(char);
			});

			idx_main_for_user = $.roster.users.map(function(user) {
				return $.roster.charsByUser(user.id).filter(function(char) {
					return char.main;
				})[0];
			});
		};

		$.roster.trigger = function() {
			$.roster.buildIndexes();
			if (GuildToolsScope) GuildToolsScope.$broadcast("roster-updated");
		};

		$.roster.charsByUser = function(user) {
			return idx_chars_by_user[user] || [];
		};

		var temp_mains = {};

		$.roster.mainForUser = function(user) {
			return idx_main_for_user[user] || temp_mains[user] || (temp_mains[user] = {
				name: $.roster.user(user).name,
				"class": 99,
				owner: Number(user),
				role: "UNKNOW",
				ilvl: 0,
				unknown: true
			});
		};
	})();

//
// Web socket
//
	(function() {

		function should_enable_compression() {
			var setting = localStorage.getItem("socket.compress");
			return setting !== "0";
		}

		var ws = null;
		var tries = 0;
		var calls = [];
		var inflight = 0;
		var queue = [];
		var init_done = false;
		var reason = null;
		var socket_url = null;
		var enable_compression = should_enable_compression();
		var message_handler = null;

		var sock_compress = new Worker("/assets/javascripts/web/sock_compress.js");

		sock_compress.onmessage = function(msg) {
			var data = msg.data;
			switch (data.$) {
				case "deflate-data":
					if (ws) ws.send(data.buf);
					break;

				case "inflate-data":
					ws_frame_handle(data.payload);
					break;
			}
		};

		function trigger_serv_update() {
			_("#loading-error-title").text("The Guild-Tools server has just been upgraded");
			_("#loading-error-text").text("You may continue what you were doing, but some features could now be broken.\nIt is recommended that you reload the client to get the latest upgrades.");
			_("#loading-error-infos").text("You may lose any unsaved work if you reload now");
			_("#loading-error").css({ display: "block", opacity: 0.9 });
			_("#loading-error-actions").css({ display: "block" });
		}

		$.getInflight = function() {
			return inflight;
		};

		$.wsAPI = {
			"chat:user:connect": function(user) {
				$.online = $.online.filter(function(id) { return id !== user; });
				$.online.push(user);
			},

			"chat:user:disconnect": function(user) {
				$.online = $.online.filter(function(id) { return id !== user; });
			},

			"chat:shoutbox:msg": function(msg) {
				$.shoutbox.push(msg);
				if ($.shoutbox.length > 100) {
					$.shoutbox.shift();
				}
			},

			"roster:char:update": function(char) {
				if ($.roster.chars[char.id]) {
					char.$$hashKey = $.roster.chars[char.id].$$hashKey;
				}
				$.roster.chars[char.id] = char;
				$.roster.trigger();
			},

			"roster:char:delete": function(id) {
				delete $.roster.chars[id];
				$.roster.trigger();
			},

			"event": function(event) {
				if ($.wsAPI[event.name]) return $.wsAPI[event.name](event.arg);
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

			var run = false;
			var wrapped_cb = function() {
				if (run) return;
				run = true;
				clearTimeout(timeout);
				setTimeout(function() {
					--inflight;
				}, 100);
				if (cb) cb.apply(null, arguments);
			};

			var timeout = setTimeout(function() {
				wrapped_cb(new Error("TIMEOUT"));
			}, 10000);

			++inflight;
			if (!Pace.running) Pace.restart();

			$.exec(cmd, arg, wrapped_cb);
		};

		$.dump = function(cmd, arg) {
			$.call(cmd, arg, function(err, data) {
				if (err) return console.error(err);
				console.log(data);
			});
		};

		function ws_read(frame) {
			if (enable_compression) {
				sock_compress.postMessage({ $: "inflate", buf: frame.data });
			} else {
				ws_frame_handle(frame.data);
			}
		}

		function ws_frame_handle(payload) {
			if (!message_handler) return;

			try {
				message_handler(null, JSON.parse(payload));
			} catch (e) {
				console.error(e, msg.data);
				message_handler({ reason: "invalid handshake" });
				ws.close();
			}
		}

		function ws_setup(err) {
			var xhr = new XMLHttpRequest();
			xhr.open("GET", "/api/socket_url", true);
			xhr.responseType = "text";

			xhr.onload = function () {
				if (this.status == 200) {
					socket_url = this.responseText;
				} else {
					socket_url = document.location.origin.replace(/^http/, "ws") + "/socket";
				}

				if (enable_compression) socket_url += "_z";
				$.wsConnect(err);
			};

			xhr.send();
		}

		function ws_onmessage(err, msg) {
			if (err) {
				bugsack_send(err);
				return;
			}

			var cmd = msg.$;
			var id = msg["#"];
			var arg = msg["&"];
			var handler = typeof id === "number" ? calls[id] : null;

			var updateTimeout = null;

			function triggerUpdate() {
				if (!GuildToolsScope) return;
				if (updateTimeout) clearTimeout(updateTimeout);
				updateTimeout = setTimeout(function() {
					GuildToolsScope.safeApply();
				}, 30);
			}

			switch (cmd) {
				case "ack":
				case "res":
					if (typeof handler !== "function") return;
					try {
						handler.call(null, null, arg);
						triggerUpdate();
					} catch (e) {
						console.error(e);
						bugsack_send(e);
					}

					delete calls[msg.results];
					break;

				case "nok":
				case "err":
					if (typeof handler !== "function") return;
					try {
						handler.call(null, arg);
						if (GuildToolsScope && cmd === "nok") {
							GuildToolsScope.error(arg || "An error occurred. Please try again.");
							triggerUpdate();
						}
					} catch (e) {
						console.error(e);
						bugsack_send(e);
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
						triggerUpdate();
					} catch (e) {
						console.error(e);
						bugsack_send(e);
					}
			}
		}

		$.wsConnect = function(err) {
			if (!socket_url) return ws_setup(err);

			ws = new WebSocket(socket_url);
			if (enable_compression)
				ws.binaryType = "arraybuffer";

			function cb(info) {
				if (err) return err(info);
				err = null;
			}

			ws.onclose = function(e) {
				ws = null;
				return cb(e);
			};

			ws.onmessage = ws_read;

			message_handler = function(err, msg) {
				if (err) return cb(err);

				if (msg.service !== "GuildTools" || msg.protocol !== "GTP2" || msg.version !== "5.0") {
					return $.error(
						"Unable to validate game version",
						"This may be caused by file corruption or the interference of another program.",
						"Error #113"
					);
				}

				$.exec("socket:compression", true );

				if ($.rev && $.rev !== msg.rev) {
					trigger_serv_update();
				}

				$.rev = msg.rev;

				ws.onclose = $.wsReconnect;
				message_handler = ws_onmessage;

				$.wsAuth();
			};
		};

		$.wsAuth = function(redirect) {
			$.call("auth", { session: localStorage.getItem("session.token") }, function(err, res) {
				if (err) return;
				$.user = res.user;

				if ($.user) {
					$.user.ready = res.ready;
					$.syncOnlines();
				}

				if (!init_done) {
					init_done = true;
					angular.bootstrap(document, ["GuildTools"]);
					_("body").append("<div id='loading-done'></div>");
				} else {
					GuildToolsScope.syncContext();
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
				if (enable_compression) {
					sock_compress.postMessage({ $: "deflate", payload: JSON.stringify(m) });
				} else {
					ws.send(JSON.stringify(m));
				}
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

			if (GuildToolsScope) {
				GuildToolsScope.popupErrorText = "Reconnecting...";
				GuildToolsScope.safeApply();
			}

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
				setTimeout(function() {
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
			try {
				call[1](data);
			} catch (e) {
			}
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

