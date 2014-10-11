var GuildTools = angular.module("GuildTools", ["ngRoute", "ngAnimate"]);
var GuildToolsScope = null;
var GuildToolsLocation = null;


//Routing

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
		.when("/profile/:id?", {
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

	$locationProvider.html5Mode({ enabled: true, requireBase: false });
	$animateProvider.classNameFilter(/animated|customAnimated/);
});

GuildTools.directive("ngContextmenu", function($parse) {
	return function(scope, element, attrs) {
		var fn = $parse(attrs.ngContextmenu);
		element.bind("contextmenu", function(event) {
			scope.$apply(function() {
				event.preventDefault();
				fn(scope, {$event: event});
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

GuildTools.filter("capitalize", function() {
	return function(input) {
		return input.charAt(0).toUpperCase() + input.slice(1).toLowerCase();
	};
});
