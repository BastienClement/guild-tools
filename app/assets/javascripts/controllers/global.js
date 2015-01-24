var GuildToolsScope, GuildToolsLocation;

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
	$scope.moment = moment;

	$scope.timeago = function(date) {
		return _.timeago(new Date(date));
	};

	_(window).resize(function() {
		$scope.$broadcast("resize");
	});

	//
	// --- Location ---
	//

	function close_ui_popups() {
		$scope.modal();
		_("#tooltip").hide();
		_(".wowhead-tooltip").hide();
	}

	$scope.$on("$locationChangeStart", function(e, next, prev) {
		close_ui_popups();
		setTimeout(close_ui_popups, 500);
	});

	$scope.goto = function(path, override, replace) {
		if (!$.user || !$.user.ready) return;
		$location.path(path);
		if (replace) $location.replace();
	};

	$scope.restrict = function(custom) {
		if ($.user) {
			if (!$.user.ready && (custom != "function" || custom())) {
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

	$scope.isPromoted = function() {
		return $.user && $.user.promoted;
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
				$scope.goto("/dashboard");
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

	$scope.groupNames = {
		"10": "Guest",
		"8": "Apply",
		"12": "Casual",
		"9": "Member",
		"11": "Officer"
	};

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
	$scope.navigator_fallback = null;

	var navigators = {
		"dashboard": [
			{ id: "main", title: "Dashboard", target: "/dashboard" }
		],
		"calendar": [
			{ id: "main", title: "Calendar", target: "/calendar" },
			{ id: "absences", title: "Absences", target: "/calendar/abs" },
			{ id: "composer", title: "Composer", target: "/calendar/composer", visible: function() { return $.user.promoted; } }
		],
		"profile": [
			{ id: "main", title: "Profile", target: "/profile" }
		],
		"roster": [
			{ id: "main", title: "Roster", target: "/roster" }
		]
	};

	$scope.setNavigator = function(name, tab, overrides) {
		$scope.navigator_current = name;
		$scope.navigator_tab = tab;
		$scope.navigator_fallback = null;

		if (name && navigators[name]) {
			$scope.navigator = [];
			navigators[name].forEach(function(item) {
				var id = item.id;
				var title = item.title;
				var target = item.target;
				var visible = item.visible;

				if (typeof visible == "function") {
					if (!visible()) return;
				}

				if (tab == id) {
					document.title = title || "Guild-Tools";
				}

				if (overrides && overrides[id]) {
					title = overrides[id][0];
					target = overrides[id][1];
				}

				$scope.navigator.push([id, title, function() {
					if (typeof target === "function") {
						try {
							target();
						} catch (e) {}
					} else {
						$scope.goto(target);
					}
				}]);
			});
		} else {
			$scope.navigator = null;
			document.title = "Guild-Tools";
		}
	};

	$scope.setAutoNavigator = function(name) {
		$scope.navigator_current = null;
		$scope.navigator_tab = null;
		$scope.navigator = null;
		$scope.navigator_fallback = name;
		document.title = name;
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

	$scope.prompt = function(config) {
		$scope.modal("dialog-prompt", config, config.secure);
	};
});
