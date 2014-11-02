GuildTools.controller("GlobalCtrl", function($scope, $location, $http) {
	GuildToolsScope = $scope;
	GuildToolsLocation = $location;

	$scope.safeApply = function(fn) {
		var phase = this.$root.$$phase;
		if (phase == '$apply' || phase == '$digest') {
			if (fn && (typeof(fn) === 'function')) {
				fn();
			}
		} else {
			this.$apply(fn);
		}
	};

	setInterval(function() {
		$scope.safeApply();
	}, 60000);

	$scope.$ = $;
	$scope.alert = alert;
	$scope.console = console;

	$scope.timeago = function(date) {
		return _.timeago(new Date(date));
	};

	_(window).resize(function() {
		$scope.$broadcast("resize");
	});

	$scope.breadcrumb = (function() {
		var baseURL = document.location.origin;

		var stack = [
			{
				icon: "home",
				name: "Dashboard",
				location: baseURL + "/dashboard",
				path: "/dashboard",
				root: true
			}
		];

		var forward = [];

		var location_data = [
			["/welcome", "planet", "Getting Started"],
			["/about", "tools", "Guild-Tools"],

			[/^\/profile(\/\d+)?$/, "helm2", ""],

			["/calendar", "calendar", "Calendar"],
			[/^\/calendar\/event\/\d+$/, "events", ""],
			["/calendar/abs", "afk", "Absences"],

			["/social", "forum", "Social"],
			["/roster", "roster", "Roster"],
			["/whishlist", "whishlist", "Whishlist"],
			["/blueprint", "blueprint", "Blueprint"]
		];

		function resolve(path, override) {
			if (path[0] !== "/") {
				path = path.slice(baseURL.length);
			}

			for (var i = 0; i < location_data.length; ++i) {
				var entry = location_data[i];
				if ((entry[0] instanceof RegExp && entry[0].test(path)) || entry[0] === path) {
					return {
						icon: (override && override.icon) || entry[1],
						name: (override && override.name) || entry[2],
						location: baseURL + path,
						path: path
					};
				}
			}
		}

		if ($location.absUrl() !== stack[0].location) {
			stack.push(resolve($location.path()) || {
				icon: "page",
				name: "Current page",
				location: $location.absUrl()
			});
		}

		$scope.$on("$locationChangeStart", function(e, next, prev) {
			$scope.modal();
			_("#tooltip").hide();
			if (next === prev) return;
			if (stack.length > 1 && next === stack[stack.length - 2].location) {
				forward.unshift(stack.pop());
				updateTitle();
			} else if (forward.length && forward[0].location === next) {
				stack.push(forward.shift());
			} else if ($.user) {
				$scope.breadcrumb.updateTitle();
			}
		});

		var last = null;

		function updateTitle() {
			var s = $scope.breadcrumb.stack;
			var f = s[s.length - 1];

			if (/^\s*$/.test(f.name)) return;
			var name = f.name === "Current page" ? "GuildTools" : f.name;
			document.title = name;

			if (last !== f) {
				last = f;
				ga('send', 'pageview', {
					'page': f.path,
					'title': name
				});
			}
		}

		return {
			stack: stack,
			updateTitle: updateTitle,

			push: function(path, override, replace, test) {
				if (!$.user || !$.user.ready) return;

				var target = resolve(path, override);
				if (!target || stack[stack.length - 1].path === path) return;
				if (test) return true;

				if (path === "/") return $scope.breadcrumb.rewind("/");

				for (var i = 0; i < stack.length; ++i) {
					if (stack[i].location === target.location) {
						//stack[i].icon = target.icon;
						//stack[i].name = target.name;
						return $scope.breadcrumb.rewind(target.location);
					}
				}

				$location.path(target.path);
				forward = [];

				if (replace) {
					$location.replace();
					stack.pop();
				}

				stack.push(target);
				updateTitle();
			},

			override: function(override) {
				if (!override) return;
				var cur = stack[stack.length - 1];
				if (cur.root) return;
				if (override.icon) cur.icon = override.icon;
				if (override.name) cur.name = override.name;
				updateTitle();
			},

			replace: function(path, override) {
				$scope.breadcrumb.push(path, override, true);
			},

			rewind: function(target) {
				if (!$.user || !$.user.ready) return;
				if (target === stack[stack.length - 1].location || target === stack[stack.length - 1].path) return;

				for (var i = 0; ; --i) {
					var entry = stack.pop();
					if (entry.location === target || entry.path === target || entry.root) {
						stack.push(entry);
						$location.path(entry.path);
						break;
					} else {
						forward.unshift(entry);
					}
				}

				updateTitle();
			},

			reset: function(path) {
				if ($scope.breadcrumb.push(path, false, false, true)) {
					$scope.breadcrumb.rewind("/");
					$scope.breadcrumb.push(path);
				}
			},

			current: function(path) {
				return path === stack[stack.length - 1].path;
			}
		};
	})();

	if ($.user) $scope.breadcrumb.updateTitle();

	$scope.restrict = function() {
		if ($.user) {
			if (!$.user.ready) {
				$location.path("/welcome").replace();
				return true;
			} else {
				return false;
			}
		} else {
			$location.path("/login").replace();
			return true;
		}
	};

	$scope.gameData = {};

	$http({
		url: "/api/gamedata",
		dataType: "json",
		method: "GET"
	}).success(function(data) {
		$scope.gameData = data;
	});

	var ctx_loader = null;
	var ctx_params = null;
	var ctx_valid = false;
	var ctx_handlers = {};

	$scope.setContext = function(loader, params, handlers) {
		ctx_loader = loader;
		ctx_params = params;
		ctx_handlers = handlers;

		if (!loader) {
			$.exec("events:unbind");
			ctx_valid = false;
			return;
		}

		$scope.syncContext();
	};

	$scope.syncContext = function() {
		if (!ctx_loader) return;

		ctx_valid = false;
		var ctx_inflight = ctx_loader;

		$.call(ctx_loader, ctx_params, function(err, res) {
			if (ctx_inflight !== ctx_loader) return;
			if (err) {
				$scope.error("Error while switching contexts");
				$scope.breadcrumb.rewind("/dashboard");
			} else {
				ctx_valid = true;
				if (typeof ctx_handlers.$ === "function") {
					ctx_handlers.$(res);
				}
			}
		});
	};

	$scope.dispatchEvent = function(event, arg) {
		if (!ctx_valid) return;
		if (!ctx_handlers[event]) return;
		ctx_handlers[event](arg);
	};

	$scope.menuContent = null;
	var ctxMenu = _("#context-menu");
	var opening = false;

	$scope.menu = function(menu, event) {
		event.stopPropagation();
		if (opening) return;
		opening = true;

		$scope.menuContent = menu.filter(function(item) { return (typeof item.visible != "boolean" || item.visible); });

		ctxMenu.css({
			opacity: 0,
			display: "block",
			top: "-500px",
			left: "-500px"
		});

		setTimeout(function() {
			opening = false;

			var left = event.pageX;
			var top = event.pageY + 5;

			var width = ctxMenu.outerWidth();
			var height = ctxMenu.outerHeight();

			if (left + width > _(window).width()) {
				left -= width;
			}

			if (top + height > _(window).height()) {
				top -= height + 10;
			}

			ctxMenu.css({
				opacity: 1,
				top: top + "px",
				left: left + "px"
			});

			_("#tooltip").css("opacity", 0);
		}, 50);
	};

	$scope.menuClose = function() {
		ctxMenu.css("display", "none");
		_("#tooltip").css("opacity", 1);
		$scope.menuContent = null;
	};

	_(document).mousedown($scope.menuClose);

	ctxMenu.mousedown(function(e) {
		e.stopPropagation();
	});

	$scope.modalView = null;
	$scope.modalCtx = null;
	$scope.modalSecure = false;

	$scope.modal = function(view, ctx, secure) {
		if (view) {
			ga('send', 'event', 'modal', 'display', view);
			$scope.modalCtx = ctx;
			$scope.modalView = "/views/" + view + ".html";
			$scope.modalSecure = !!secure;
		} else {
			$scope.modalView = null;
			$scope.modalCtx = null;
		}
	};

	$scope.modalClose = function(ev) {
		if (ev.currentTarget === ev.target && !$scope.modalSecure) {
			$scope.modal();
		}
	};

	$scope.monthNames = [
		"January",
		"February",
		"March",
		"April",
		"May",
		"June",
		"July",
		"August",
		"September",
		"October",
		"November",
		"December"
	];

	$scope.dayNames = [
		"Monday",
		"Tuesday",
		"Wednesday",
		"Thursday",
		"Friday",
		"Saturday",
		"Sunday"
	];

	$scope.popupErrorText = null;
	var popupErrorTimeout = null;

	$scope.error = function(text) {
		clearTimeout(popupErrorTimeout);
		$scope.popupErrorText = text;
		setTimeout(function() {
			$scope.popupErrorText = null;
			$scope.safeApply();
		}, 5000);
	};

	$scope.navigator = null;
	$scope.navigator_current = null;
	$scope.navigator_tab = null;

	var navigators = {
		"dashboard": [
			["main", "Dashboard", "/dashboard"]
		],
		"calendar": [
			["main", "Calendar", "/calendar"],
			["lockout", "Lockout", "/calendar/lockout"],
			["absences", "Absences", "/calendar/abs"]
		],
		"profile": [
			["main", "Profile", "/profile"]
		]
	};

	$scope.setNavigator = function(name, tab, overrides) {
		$scope.navigator_current = name;
		$scope.navigator_tab = tab;

		if (name && navigators[name]) {
			$scope.navigator = [];
			navigators[name].forEach(function(item) {
				var id = item[0];
				var name = item[1];
				var action = item[2];

				if (overrides && overrides[id]) {
					name = overrides[id][0];
					action = overrides[id][1];
				}

				$scope.navigator.push([id, name, function() {
					if (typeof action === "function") {
						try {
							action();
						} catch (e) {
						}
					} else {
						$scope.breadcrumb.push(action);
					}
				}]);
			});
		} else {
			$scope.navigator = null;
		}
	};

	$scope.userMenu = [
		{
			icon: "awe-logout", text: "Logout",
			action: function() {
				$.exec("auth:logout", function() {
					$.error = function() {
					};
					localStorage.removeItem("session.token");
					location.reload();
				});
			},
			order: 10
		}
	];

	$scope.openURL = function(url) {
		window.open(url);
	};
});
