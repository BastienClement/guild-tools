
var GuildTools = angular.module("GuildTools", ["ngRoute", "ngAnimate"]);
var GuildToolsScope = null;

//
// Routing
//
GuildTools.config(function($routeProvider, $locationProvider, $animateProvider) {
	$routeProvider
		.when("/login", {
			templateUrl: "/assets/views/login.html",
			controller: "LoginCtrl"
		})
		.when("/welcome", {
			templateUrl: "/assets/views/welcome.html",
			controller: "WelcomeCtrl"
		})
		.when("/dashboard", {
			templateUrl: "/assets/views/dashboard.html",
			controller: "DashboardCtrl"
		})
		.when("/player/profile/:id?", {
			templateUrl: "/assets/views/player-profile.html",
			controller: "ProfileCtrl"
		})
		.when("/calendar", {
			templateUrl: "/assets/views/calendar.html",
			controller: "CalendarCtrl"
		})
		.when("/calendar/event/:id", {
			templateUrl: "/assets/views/calendar-event.html",
			controller: "CalendarEventCtrl"
		})
		.when("/calendar/slacks", {
			templateUrl: "/assets/views/slacks.html",
			controller: "SlacksCtrl"
		})
		.when("/forum", {
			templateUrl: "/assets/views/forum.html",
			controller: "ForumCtrl"
		})
		.when("/roster", {
			templateUrl: "/assets/views/roster.html",
			controller: "RosterCtrl"
		})
		.when("/whishlist", {
			templateUrl: "/assets/views/whishlist.html",
			controller: "WhishlistCtrl"
		})
		.when("/blueprints", {
			templateUrl: "/assets/views/blueprints.html",
			controller: "BlueprintsCtrl"
		})
		.otherwise({
			redirectTo: function() {
				return $.user ? "/dashboard" : "/login";
			}
		});

	$locationProvider.html5Mode(true);
	$animateProvider.classNameFilter(/animated/);
});

GuildTools.directive("ngContextmenu", function($parse) {
    return function(scope, element, attrs) {
        var fn = $parse(attrs.ngContextmenu);
        element.bind("contextmenu", function(event) {
            scope.$apply(function() {
                event.preventDefault();
                fn(scope, {$event:event});
            });
        });
    };
});

GuildTools.directive("ngTooltip", function() {
	var tooltip = _("#tooltip");
	
	return function(scope, element, attrs) {
		var x, y;
		var open = false;
		
		function update(event) {
			x = event.clientX + 10;
			y = _(window).height() - event.clientY + 10;
			
			var width = tooltip.outerWidth();
			var height = tooltip.outerHeight();
			
			if (x + width > _(window).width()) {
				x -= width + 20;
			}
			
			if (y - height < 0) {
				y += height + 20;
			}
			
			tooltip.css({
				left: x,
				bottom: y
			});
		}
		
		var initDelay = null;
		
		scope.$on("$destroy", function() {
			if (open) tooltip.hide();
		});
		
		element.bind("mouseover", function(event) {
			var tooltip_value = attrs.ngTooltip;
		
			// Delayed display
			if (tooltip_value[0] === "#") {
				tooltip.css("opacity", 0);
				tooltip_value = tooltip_value.slice(1);
				initDelay = setTimeout(function() {
					tooltip.css("opacity", 1);
				}, 1000);
			} else {
				tooltip.css("opacity", 1);
			}
			
			// Raw text data
			if (tooltip_value[0] === "$") {
				tooltip.text(tooltip_value.slice(1));
			} else {
				var target = _(attrs.ngTooltip, element);
				if (target.length) {
					tooltip.html(target.html());
				} else {
					tooltip.text(tooltip_value);
				}
			}
			
			open = true;
			tooltip.show();
			update(event);
        });
		
		element.bind("mouseout", function(event) {
			open = false;
			clearTimeout(initDelay);
			tooltip.hide();
        });
		
		element.bind("mousemove", function(event) {
			update(event);
		});
    };
});

GuildTools.filter("capitalize", function () {
    return function(input) {
        return input.charAt(0).toUpperCase() + input.slice(1).toLowerCase();
    };
});

//
// Global Tools
//
GuildTools.controller("GlobalCtrl", function($scope, $location) {
	GuildToolsScope = $scope;
	
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
				if (!$.user || !$.user.chars.length) return;
				
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
				if (!$.user || !$.user.chars.length) return;
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
			if (!$.user.chars || !$.user.chars.length) {
				$location.path("/welcome").replace();
			} else {
				return false;
			}
		} else {
			$location.path("/login").replace();
			return true;
		}
	};
	
	$scope.gameData = {};
	
	$.call("fetchGameData", function(err, data) {
		if (err) return $scope.breadcrumb.rewind('/dashboard');
		$scope.gameData = data;
	});
	
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

//
// Title bar manager
//
GuildTools.controller("TitleBarCtrl", function($scope) {
	$scope.titleMenu = [
		{
			icon: "awe-help", text: "About", action: function() {
				$scope.breadcrumb.push("/about");
			}, order: 1
		},
		{ 
			icon: "awe-cog", text: "Settings", action: function() {
				$scope.breadcrumb.push("/settings");
			}, order: 2
		},
		{ separator: true, order: 2.5 },
		{ 
			icon: "awe-arrows-cw", text: "Reload application", action: function() {
				ga('send', 'event', 'app-menu', 'action', 'reload');
				location.reload();
			}, order: 3
		},
		{
			icon: "awe-logout", text: "Logout", action: function() {
				$.exec("logout", function() {
					ga('send', 'event', 'app-menu', 'action', 'logout', {
						hitCallback: function() {
							$.error = function() {};
							$.exec('close');
							localStorage.removeItem("session.token");
							location.reload();
						}
					});
				});
			}, order: 10
		},
	];
	
	if (!$$) {
		$scope.titleMenu.push({
			icon: "awe-download", text: "Download client", action: function() { 
				ga('send', 'event', 'app-menu', 'action', 'download-client');
				//document.location.href = "/tools.exe";
				window.open("/tools.exe", "_blank");
			}, order: 5
		});
	} else {
		$scope.titleMenu.push({
			icon: "awe-wrench", text: "Developer Tools", action: function() {
				ga('send', 'event', 'app-menu', 'action', 'dev-tools');
				$$.win.openDevTools();
			}, order: 5
		});
	}
	
	$scope.userMenu = [
		/*{
			icon: "status-dot", text: "Online", action: function() {
			}, order: 1
		},
		{ 
			icon: "status-dot away", text: "Away", action: function() {
			}, order: 2
		},
		{ 
			icon: "status-dot busy", text: "Busy", action: function() {
			}, order: 3
		},
		{ separator: true, order: 4 },*/
		{
			icon: "awe-logout", text: "Logout", action: function() {
				$.exec("logout", function() {
					$.error = function() {};
					$.exec('close');
					localStorage.removeItem("session.token");
					location.reload();
				});
			}, order: 10
		},
	];
});

//
// Login Screen
//
GuildTools.controller("LoginCtrl", function($scope, $location) {
	$scope.inflight = false;
	$scope.user = localStorage.getItem("login.user");
	$scope.pass = "";
	
	$scope.$watch("user", function(n, o) {
		if (n != o) localStorage.setItem("login.user", n || "");
	});
	
	$scope.do_login = function() {
		if ($scope.inflight) return;
		$scope.inflight = true;
		
		$.call("prepareLogin", $scope.user, function(err, res) {
			if (!res) {
				$scope.inflight = false;
				//$scope.error = "Cet identifiant n'existe pas";
				$scope.user = "";
				$scope.pass = "";
				_("#login-user").focus();
				return;
			}
			
			var pass = CryptoJS.MD5(phpbb_hash($scope.pass, res.setting) + res.salt).toString();
			$.call("login", $scope.user, pass, function(err, res) {
				$scope.inflight = false;
				
				if (!res) {
					//$scope.error = "Mot de passe incorrect";
					$scope.pass = "";
					_("#login-pass").focus();
					return;
				}
				
				$.user = res.user;
				$.user.chars = res.chars;
				localStorage.setItem("session.token", res.token);
				ga('set', 'userId', $.user.id);
				ga('send', 'event', 'session', 'begin', $.anonSessId());
				
				if (res.chars.length) {
					$location.path("/dashboard").replace();
				} else {
					$location.path("/welcome").replace();
				}
			});
		});
	};
});

//
// Welcome Screen
//
GuildTools.controller("WelcomeCtrl", function($scope, $location) {
	if (!$.user) {
		$location.path("/login");
		return;
	}
	
	$scope.embedWelcome = true;
});

//
// Dashboard view
//
GuildTools.controller("DashboardCtrl", function($scope, $location) {
	if ($scope.restrict()) return;
	
	$.exec("resetContext");
	
	$scope.go = function(path) {
		$location.path(path);
	};
	
	$scope.onlineSort = "name";
});

//
// Calendar view
//
GuildTools.controller("CalendarCtrl", function($scope) {
	if ($scope.restrict()) return;
	
	var now = new Date();	
	$scope.month = now.getMonth();
	$scope.year = now.getFullYear();
	
	$scope.monthNext = function() { 
		$scope.month += 1;
		if ($scope.month > 11) {
			$scope.month = 0;
			$scope.year += 1;
		}
		$scope.buildCalendar();
	};
	
	$scope.monthPrev = function() {
		$scope.month -= 1;
		if ($scope.month < 0) {
			$scope.month = 11;
			$scope.year -= 1;
		}
		$scope.buildCalendar();
	};
	
	$scope.monthCurrent = function() {
		var now = new Date();
		if ($scope.month === now.getMonth() && $scope.year === now.getFullYear()) return;
		$scope.month = now.getMonth();
		$scope.year = now.getFullYear();
		$scope.buildCalendar();
	};
	
	function zero_pad(i) {
		return (i < 10) ? "0" + i : "" + i;
	}
	
	$scope.data = [];
	$scope.events = {};
	
	function pushEvent(event) {
		if (!$scope.events[event.date]) {
			$scope.events[event.date] = [event];
		} else {
			$scope.events[event.date].push(event);
		}
	}
	
	$scope.buildCalendar = function() {
		$scope.setContext("calendar:main", [$scope.month + 1, $scope.year], {
			$: function(events) {
				$scope.events = {};
				events.forEach(pushEvent);
			},
			
			EventCreated: function(event) {
				pushEvent(event);
			},
			
			EventDeleted: function(eventid) {
				for (var day in $scope.events) {
					for (var i in $scope.events[day]) {
						var event = $scope.events[day][i];
						if (event.id === eventid) {
							$scope.events[day].splice(i, 1);
							return;
						}
					}
				}
			},
			
			AnswerUpdated: function(eventid, answer) {
				for (var day in $scope.events) {
					for (var i in $scope.events[day]) {
						var event = $scope.events[day][i];
						if (event.id === eventid) {
							event.answer = answer;
							return;
						}
					}
				}
			}
		});
		
		var last_month = new Date($scope.year, $scope.month, 0);
		var days_in_last_month = last_month.getDate();
		var last_month_id = last_month.getMonth();
		var last_month_year = last_month.getFullYear();
		
		var next_month = new Date($scope.year, $scope.month + 1, 1);
		var next_month_id = next_month.getMonth();
		var next_month_year = next_month.getFullYear();
		
		var days_in_month = (new Date($scope.year, $scope.month + 1, 0)).getDate();
		var first_month_day = ((new Date($scope.year, $scope.month, 1).getDay()) + 6) % 7;
		
		var today = new Date();
		
		for(var r = 0; r < 6; r++) {
			if(!$scope.data[r]) $scope.data[r] = [];
			
			for(var c = 0; c < 7; c++) {
				if(!$scope.data[r][c]) $scope.data[r][c] = {};
				
				var day = 7 * r + c - first_month_day + 1;
				var month = $scope.month;
				var year = $scope.year;
				
				if(day < 1) {
					day = days_in_last_month + day;
					month = last_month_id;
					year = last_month_year;
				} else if(day > days_in_month) {
					day = day - days_in_month;
					month = next_month_id;
					year = next_month_year;
				}
				
				var cell = $scope.data[r][c];
				cell.day = day;
				cell.month = month + 1;
				cell.year = year;
				cell.inactive = (month != $scope.month);
				cell.today = (year == today.getFullYear() && month == today.getMonth() && day == today.getDate());
				cell.id = year + "-" + zero_pad(month + 1) + "-" + zero_pad(day);
			}
		}
	};
	
	$scope.buildCalendar();
	
	$scope.open = function(id) {
		$scope.breadcrumb.push("/calendar/event/" + id);
	};
	
	$scope.accept = function(id, ev) {
		ev.stopPropagation();
		$.call("answerEvent", id, 1, null);
	};
	
	$scope.decline = function(id, ev) {
		ev.stopPropagation();
		$.call("answerEvent", id, 2, null, null);
	};
	
	$scope.eventMenu = function(event, ev) {
		var menu = [];
		
		var hasDelete = false;
		var hasControls = false;
		
		if (event.owner === $.user.id || $.user.officer) {
			hasDelete = true;
			menu.push({
				icon: "awe-trash", text: "Delete", action: function() {
					if (confirm("Are you sure?"))
						$.call("deleteEvent", event.id);
				}, order: 10
			});
		}
		
		if (event.type !== 4) {
			menu.push({
				icon: "awe-link-ext-alt", text: "Open", action: function() {
					$scope.open(event.id);
				}, order: 1
			});
			
			if (event.state === 0) {
				hasControls = true;
				menu.push({
					icon: "awe-ok", text: "Accept", action: function() {
						$scope.accept(event.id, ev);
					}, order: 3
				});
				
				menu.push({
					icon: "awe-cancel", text: "Decline", action: function() {
						$scope.decline(event.id, ev);
					}, order: 4
				});
			}
		}
		
		if (hasDelete && hasControls) {
			menu.push({ separator: true, order: 5 });
		}
		
		if (menu.length) {
			$scope.menu(menu, ev);
		}
	};
	
	$scope.formatTime = function(time) {
		var str = time.toString();
		return str.slice(0,-2) + ':' + str.slice(-2);
	};
	
	$scope.typeLabel = function(t) {
		switch (t) {
			case 1: return "Guild event";
			case 2: return "Public event";
			case 3: return "Private event";
			case 4: return "Announce";
		}
	};
	
	$scope.stateClass = function(e, onlyState) {
		if (!onlyState) {
			if (e.type === 4) return "note";
			if (e.state === 1) return "closed";
			if (e.state === 2) return "canceled";
		}
		
		switch (e.answer) {
			case 1: return "accepted";
			case 2: return "declined";
		}
	};
	
	$scope.stateLabel = function(a) {
		switch (a) {
			case 1: return "Accepted";
			case 2: return "Declined";
			default: return "Non register";
		}
	};
});

//
// Calendar Add Event Dialog
//
GuildTools.controller("CalendarAddEventCtrl", function($scope) {
	if ($scope.restrict()) return;
	
	$scope.working = false;
	$scope.step = 1;
	
	$scope.eventTitle = "";
	$scope.eventDesc = "";
	$scope.eventType = 1;
	$scope.eventHour = 0;
	$scope.eventMin = 0;
	
	var now = new Date();
	var next;
	if (now.getMonth() == 11) {
		next = new Date(now.getFullYear() + 1, 0, 1);
	} else {
		next = new Date(now.getFullYear(), now.getMonth() + 1, 1);
	}
	
	function zero_pad(i) {
		return (i < 10) ? "0" + i : "" + i;
	}
	
	function buildCalendar(date) {
		var year = date.getFullYear();
		var month = date.getMonth();
		var days_in_month = (new Date(year, month + 1, 0)).getDate();
		var first_month_day = ((new Date(year, month, 1).getDay()) + 6) % 7;
		
		var today = new Date();
		var calendar = [[]];
		var cur_line = 0;
		
		function pushDay(day) {
			if (calendar[cur_line].length > 6) {
				++cur_line;
				calendar[cur_line] = [];
			}
			
			calendar[cur_line].push(day);
		}
		
		for (var d = 0; d < first_month_day; ++d) {
			pushDay({ old: true, text: "" });
		}
		
		for (d = 0; d < days_in_month; ++d) {
			pushDay({
				old: (year <= today.getFullYear() && month <= today.getMonth() && (d+1) < today.getDate()),
				today: (year == today.getFullYear() && month == today.getMonth() && (d+1) == today.getDate()),
				id: year + "-" + zero_pad(month + 1) + "-" + zero_pad(d + 1),
				text: (d + 1)
			});
		}
		
		return calendar;
	}
	
	$scope.thisMonth = buildCalendar(now);
	$scope.thisMonthName = $scope.monthNames[now.getMonth()];
	$scope.nextMonth = buildCalendar(next);
	$scope.nextMonthName = $scope.monthNames[next.getMonth()];
	
	var selected = [];
	var lastClick = null;
	
	$scope.isSelected = function(id) {
		return selected.some(function(d) { return d === id; });
	};
	
	$scope.select = function(ev, id) {
		ev.preventDefault();
	
		if ($scope.isSelected(id)) {
			selected = selected.filter(function(d) { return d !== id; });
			lastClick = null;
			return;
		}
			
		function next(date) {
			return new Date(date.getFullYear(), date.getMonth(), date.getDate() + 1);
		}
		
		function toId(date) {
			return date.getFullYear() + "-" + zero_pad(date.getMonth() + 1) + "-" + zero_pad(date.getDate());
		}
		
		if (ev.ctrlKey && $.user.officer) {
			selected.push(id);
		} else if(ev.shiftKey && lastClick && $.user.officer) {
			if (id === lastClick) return;
			var d1 = new Date(id);
			var d2 = new Date(lastClick);
			
			var from = d1 < d2 ? d1 : d2;
			var to = d1 < d2 ? d2 : d1;
			
			var day = from;
			var end = toId(to);
			
			for (var i = 0;; ++i) {
				if (i > 62) {
					selected = [];
					break;
				}
				
				var dayid = toId(day);

				if (!$scope.isSelected(dayid)) {
					selected.push(dayid);
				}
				
				if (dayid == end) {
					break;
				}
				
				day = next(day);
			}
		} else {
			selected = [id];
		}
		
		lastClick = id;
	};
	
	$scope.canContinue = function() {
		return selected.length > 0;
	};
	
	$scope.nextStep = function() {
		$scope.step = 2;
		setTimeout(function() {
			_("#calendar-add-event-title").focus();
		}, 200);
		
		$scope.eventTitle = "";
		$scope.eventDesc = "";
		$scope.eventType = $.user.officer ? 1 : 3;
		
		$scope.eventHour = 0;
		$scope.eventMin = 0;
		$scope.clockMinutes = false;
	};
	
	$scope.clockMinutes = false;
	
	$scope.setHour = function(hour) {
		$scope.eventHour = hour;
		$scope.clockMinutes = true;
	};
	
	$scope.setMinutes = function(min) {
		$scope.eventMin = min;
		_("#calendar-add-event-title").focus();
	};
	
	$scope.backToHours = function() {
		$scope.clockMinutes = false;
	};
	
	$scope.doCreate = function() {
		$scope.working = true;
		$.call("createEvent", {
			title: $scope.eventTitle,
			desc: $scope.eventDesc,
			type: $scope.eventType,
			hour: $scope.eventHour,
			min: $scope.eventMin,
			dates: selected
		}, function(err) {
			$scope.working = false;
			if (!err) $scope.modal();
		});
	};
});

GuildTools.controller("CalendarEventCtrl", function($scope, $location, $routeParams) {
	if ($scope.restrict()) return;
	var eventid = Number($routeParams.id);
	
	$scope.event = null;
	$scope.note = null;
	$scope.answers = [];
	$scope.tab_selected = 1;
	
	var cached_tab;
	var cached_groups;
	
	$scope.answer = 1;
	$scope.answer_note = "";
	
	var raw_answers;
	
	function build_answers() {
		$scope.answers = [];
		cached_tab = null;
		
		raw_answers.forEach(function(answer) {
			if (!answer.chars.length) {
				answer.main = {
					name: answer.username,
					main: 1,
					"class": 99,
					role: "UNKNOW"
				};
			} else {
				answer.chars.some(function(char) {
					if ((!answer.main && char.main) || char.id === answer.char) {
						answer.main = char;
						return true;
					}
				});
			}
				
			if (!$scope.answers[answer.answer]) {
				$scope.answers[answer.answer] = [answer];
			} else {
				$scope.answers[answer.answer].push(answer);
			}
		});
	}
	
	$scope.setContext("calendar:event", [eventid], {
		$: function(event, answers) {
			if (!event) {
				$scope.error("This event is not available");
				$location.path("/calendar").replace();
				return;
			}
			
			if (event.answer) {
				$scope.answer = event.answer;
				$scope.answer_note = event.answer_note;
			}
		
			$scope.breadcrumb.override({ name: event.title });
			$scope.event = event;
			$scope.note = event.note;
			
			raw_answers = answers;
			build_answers();
		},
		
		AnswerUpdated: function(answer) {
			if (answer.user === $.user.id) {
				$scope.answer = answer.answer;
				$scope.answer_note = answer.note;
			}
			
			raw_answers = raw_answers.map(function(a) {
				if (a.user === answer.user) {
					return answer;
				} else {
					return a;
				}
			});
			
			build_answers();
		}
	});
	
	function zeroPad(n) {
		return String((n < 10) ? "0" + n : n);
	}
	
	$scope.formatDate = function(d) {
		var date = new Date(d);
		date = new Date(date.getTime() + date.getTimezoneOffset() * 60000);
		
		var day = zeroPad(date.getDate()) + "/" + zeroPad(date.getMonth() + 1) + "/" + date.getFullYear();
		var hour = zeroPad(date.getHours()) + ":" + zeroPad(date.getMinutes());
		return day + " - " + hour;
	};
	
	$scope.getAnswersGroups = function(tab) {
		if (cached_tab === tab) {
			return cached_groups;
		}
		
		var groups = [];
		var group = [];
		
		if (!$scope.answers[tab]) return [];
		
		$scope.answers[tab].sort(function(a, b) {
			a = a.main;
			b = b.main;
			if (a["class"] !== b["class"]) return a["class"] - b["class"];
			return a.name.localeCompare(b.name);
		});
		
		$scope.answers[tab].forEach(function(answer) {
			group.push(answer);
			if (group.length >= 2) {
				groups.push(group);
				group = [];
			}
		});
		
		if (group.length) {
			if (group.length < 2) {
				group.push({ void: true });
			}
			
			groups.push(group);
		}
		
		cached_tab = tab;
		cached_groups = groups;
		
		return groups;
	};
	
	$scope.inflight = false;
	$scope.updateAnswer = function(answer, note, char) {
		char = char ? Number(char) : null;
		$scope.inflight = true;
		$.call("answerEvent", eventid, Number(answer), note, char, function() {
			$scope.inflight = false;
		});
	};
	
	$scope.active_tab = "Raid";
	$scope.tabs = [{ title: "Raid" }];
	
	$scope.eventTimeFormat = function() {
		if (!$scope.event) return "00:00";
		var time = $scope.event.time;
		time = (time < 1000) ? "0" + time : String(time);
		return time.slice(0, 2) + ":" + time.slice(2);
	};
	
	$scope.eventStateFormat = function() {
		if (!$scope.event) return "Unknown";
		switch ($scope.event.state) {
			case 0: return "Open";
			case 1: return "Locked";
			case 2: return "Canceled";
		}
	};
	
	$scope.eventEditable = function() {
		if (!$scope.event) return false;
		return $scope.event.owner === $.user.id || $.user.officer;
	};
	
	$scope.eventMenu = function(ev) {
		var menu = [
			{
				icon: "awe-lock-open-alt", text: "Open", action: function() {
					//$.call("deleteEvent", event.id);
				}, order: 1
			},
			{
				icon: "awe-lock", text: "Lock", action: function() {
					//$.call("deleteEvent", event.id);
				}, order: 2
			},
			{
				icon: "awe-cancel", text: "Cancel", action: function() {
					//$.call("deleteEvent", event.id);
				}, order: 3
			},
			{ separator: true, order: 10 },
			{
				icon: "awe-trash", text: "Delete", action: function() {
					if (confirm("Are you sure?"))
						$.call("deleteEvent", event.id);
				}, order: 11
			}
		];
		
		$scope.menu(menu, ev);
	};
	
	$scope.raidbuffs = {
		stats: [1126, false],
		stamina: [21562, false],
		ap: [19506, false],
		sp: [1459, false],
		crit: [116781, false],
		haste: [116956, false],
		mastery: [19740, false],
		multistrike: [166916, false],
		versatility: [167187, false]
	};
	
	var buffs_table = [
		{ b: "stats", c: 11, w: 1126, i: "stats_druid" },
		{ b: "stats", c: 10, w: 115921, i: "stats_monk" },
		{ b: "stats", c: 2, opt: true, wh: 20217, i: "stats_paladin_opt" },
		
		{ b: "stamina", c: 5, w: 21562, i: "stamina_priest" },
		{ b: "stamina", c: 1, opt: true, w: 6673, i: "stamina_warrior" },
		{ b: "stamina", c: 9, opt: true, w: 166928, i: "stamina_warlock_opt" },
		
		{ b: "ap", c: 3, w: 19506, i: "ap_hunt" },
		{ b: "ap", c: 6, w: 57330, i: "ap_dk" },
		{ b: "ap", c: 1, opt: true, w: 6673, i: "ap_warrior" },
		
		{ b: "sp", c: 8, w: 1459, i: "sp_mage" },
		{ b: "sp", c: 9, w: 109773, i: "sp_warlock" },
		
		{ b: "crit", c: 10, r: "TANK", w: 116781, i: "crit_monk_nonheal" },
		{ b: "crit", c: 10, r: "DPS", w: 116781, i: "crit_monk_nonheal" },
		{ b: "crit", c: 8, w: 1459, i: "crit_mage" },
		
		{ b: "haste", c: 5, r: "DPS", w: 49868, i: "haste_priest_dps" },
		{ b: "haste", c: 4, w: 113742, i: "haste_rogue" },
		{ b: "haste", c: 7, w: 116956, i: "haste_shaman" },
		{ b: "haste", c: 6, r: "DPS", w: 55610, i: "haste_dk_dps" },
		
		{ b: "mastery", c: 7, w: 116956, i: "mastery_shaman" },
		{ b: "mastery", c: 6, r: "TANK", w: 155522, i: "mastery_dk_tank" },
		{ b: "mastery", c: 2, opt: true, w: 19740, i: "mastery_paladin_opt" },
		
		{ b: "multistrike", c: 10, r: "DPS", w: 166916, i: "multistrike_monk_dps" },
		{ b: "multistrike", c: 9, w: 109773, i: "multistrike_warlock" },
		{ b: "multistrike", c: 4, w: 113742, i: "multistrike_rogue" },
		{ b: "multistrike", c: 5, r: "DPS", w: 49868, i: "multistrike_priest_dps" },
		
		{ b: "versatility", c: 2, r: "DPS", w: 167187, i: "versatility_paladin_dps" },
		{ b: "versatility", c: 1, r: "DPS", w: 167188, i: "versatility_warrior_dps" },
		{ b: "versatility", c: 6, r: "DPS", w: 55610, i: "versatility_dk_dps" },
		{ b: "versatility", c: 11, w: 1126, i: "versatility_druid" },
	];
	
	$scope.getBuffIcon = function(key) {
		if ($scope.raidbuffs[key][1]) {
			return "/assets/buffs/" + $scope.raidbuffs[key][1] + ".jpg";
		} else {
			return "/assets/buffs/" + key + ".jpg";
		}
	};
	
	$scope.computeRaidBuffs = function() {
		$scope.raidbuffs.stamina = [166928, "stamina_warlock_opt"];
		$scope.raidbuffs.sp = [109773, "sp_warlock"];
		$scope.raidbuffs.multistrike = [109773, "multistrike_warlock"];
	};
	
	$scope.computeRaidBuffs();
});

GuildTools.controller("SlacksCtrl", function($scope, $location) {
	if ($scope.restrict()) return;
});

GuildTools.controller("ForumCtrl", function($scope, $location) {
	if ($scope.restrict()) return;
});

GuildTools.controller("RosterCtrl", function($scope, $location) {
	if ($scope.restrict()) return;
});

GuildTools.controller("WhishlistCtrl", function($scope, $location) {
	if ($scope.restrict()) return;
});

GuildTools.controller("BlueprintsCtrl", function($scope, $location) {
	if ($scope.restrict()) return;
});

//
// Player Profile
//
GuildTools.controller("ProfileCtrl", function($scope, $location, $routeParams) {
	if ($scope.restrict()) return;
	
	var userid = Number($routeParams.id || $.user.id);
	$scope.editable = (userid === $.user.id);
	
	$scope.profile = {};
	$scope.chars = [];
	
	$scope.setContext("player:profile", [userid], {
		$: function(profile, chars) {
			$scope.profile = profile;
			$scope.chars = chars;
			$scope.breadcrumb.override({ name: profile.name });
		},
		
		CharUpdated: function(id, change) {
			for (var i = 0; i < $scope.chars.length; ++i) {
				var char = $scope.chars[i];
				if (char.id === id) {
					for (var key in change) {
						char[key] = change[key];
					}
					return;
				}
			}
		},
		
		MainChanged: function(new_main) {
			for (var i = 0; i < $scope.chars.length; ++i) {
				var char = $scope.chars[i];
				char.main = (char.id == new_main);
			}
			
			$scope.chars.sort(function(a, b) {
				if (a.main !== b.main) return a.main ? -1 : 1;
				return 0;
			});
		},
		
		CharRemoved: function(id) {
			for (var i = 0; i < $scope.chars.length; ++i) {
				if ($scope.chars[i].id === id) {
					$scope.chars.splice(i, 1);
					break;
				}
			}
		},
		
		CharAdded: function(char) {
			$scope.chars.push(char);
		}
	});
	
	$scope.remove = function(id) {
		$.call("removeChar", id);
	};
	
	$scope.enable = function(id) {
		$.call("enableChar", id);
	};
	
	$scope.disable = function(id) {
		$.call("disableChar", id);
	};
	
	$scope.promote = function(id) {
		$.call("promoteChar", id);
	};
	
	$scope.role = function(id, role) {
		$.call("switchCharRole", id, role);
	};
	
	$scope.addChar = function() {
		if ($scope.chars.length >= 15) {
			return $scope.error("You may have a maximum of 15 registered characters with your account at any one time.");
		}
		$scope.modal('player-add-char');
	};
});

//
// Player Add Char Dialog
//
GuildTools.controller("PlayerAddCharCtrl", function($scope, $location) {
	if ($scope.restrict()) return;

	$scope.char = null;
	$scope.loading = false;
	$scope.role = "DPS";
	
	$scope.setRole = function(role) {
		console.log("set role");
		$scope.role = role;
	};
	
	function error(text, keepname) {
		$scope.loading = false;
		if (!keepname) {
			$scope.name = "";
			_("#add-char-name").focus();
			$scope.error(text);
		}
	}
	
	$scope.load = function() {
		if ($scope.loading) return;
		$scope.char = null;
		$scope.loading = true;
		
		$.bnQuery("character/" + $scope.server + "/" + $scope.name, function(char) {
			if (!char) return error("Unable to load requested character.");
			$.call("charIsAvailable", $scope.server, $scope.name, function(err, available) {
				if (!available) return error("This character is already registered to someone else and is not available.");
				$scope.loading = false;
				$scope.char = char;
				$scope.char.server = $scope.server;
			});
		});
	};
	
	$scope.confirm = function() {
		if ($scope.loading || !$scope.char) return;
		$scope.loading = true;
		
		$.call("addChar", $scope.char.server, $scope.char.name, $scope.role, function(err, invalid) {
			if (err || invalid)  return error("An error occured while linking this character to your account.", true);
			if ($scope.embedWelcome) {
				$location.path("/dashboard");
			} else {
				$scope.modal();
			}
			//$scope.breadcrumb.push("/player/profile");
		});
	};
});
