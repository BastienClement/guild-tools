GuildTools.controller("GlobalCtrl", function($scope, $location) {
	GuildToolsScope = $scope;
	GuildToolsLocation = $location;

	$scope.safeApply = function(fn) {
		var phase = this.$root.$$phase;
		if(phase == '$apply' || phase == '$digest') {
			if(fn && (typeof(fn) === 'function')) {
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

	$scope.breadcrumb = (function() {
		var baseURL = document.location.origin;

		var stack = [{
			icon: "home",
			name: "Dashboard",
			location: baseURL + "/dashboard",
			path: "/dashboard",
			root: true
		}];

		var forward = [];

		var location_data = [
			["/welcome", "planet", "Getting Started"],

			[/^\/player\/profile(\/\d+)?$/, "helm", ""],

			["/calendar", "calendar", "Calendar"],
			[/^\/calendar\/event\/\d+$/, "events", ""],
			["/calendar/slacks", "afk", "Absences"],

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
			if (stack.length > 1 && next === stack[stack.length-2].location) {
				forward.unshift(stack.pop());
				updateTitle();
			} else if(forward.length && forward[0].location === next) {
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

			push: function(path, override, replace) {
				if (!$.user || !$.user.ready) return;

				var target = resolve(path, override);
				if (!target || stack[stack.length - 1].path === path) return;

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
				if (target === stack[stack.length-1].location || target === stack[stack.length-1].path) return;

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

			current: function(path) {
				return path === stack[stack.length-1].path;
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

	/*$.call("fetchGameData", function(err, data) {
		if (err) return $scope.breadcrumb.rewind('/dashboard');
		$scope.gameData = data;
	});*/

	var current_ctx = null;
	var ctx_params = null;
	var ctx_handlers = {};

	$scope.setContext = function(ctx, params, handlers) {
		current_ctx = ctx;
		ctx_params = params;

		if (!ctx) {
			$.exec("resetContext");
			return;
		}

		ctx_handlers = handlers;
		$scope.syncContext();
	};

	$scope.syncContext = function() {
		$.call("setContext", current_ctx, ctx_params, function(err, args) {
			if (err || !args) return $scope.breadcrumb.rewind("/dashboard");
			if (typeof ctx_handlers.$ === "function") {
				ctx_handlers.$.apply(null, args);
			}
		});
	};

	$scope.handleMessage = function(ctx, msg, args) {
		if (current_ctx !== ctx) return;
		if (!ctx_handlers[msg]) return;
		ctx_handlers[msg].apply(null, args);
	};

	$scope.menuContent = null;
	var ctxMenu = _("#context-menu");
	var opening = false;

	$scope.menu = function(menu, event) {
		event.stopPropagation();
		if (opening || menu === $scope.menuContent) return;
		opening = true;

		$scope.menuContent = menu;

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

	_(document).click($scope.menuClose);

	ctxMenu.click(function(e) {
		e.stopPropagation();
	});

	$scope.modalView = null;

	$scope.modal = function(view) {
		if (view) {
			ga('send', 'event', 'modal', 'display', view);
			$scope.modalView = "/views/" + view + ".html";
		} else {
			$scope.modalView = null;
		}
	};

	$scope.modalClose = function(ev) {
		if (ev.currentTarget === ev.target) {
			$scope.modalView = null;
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
});
