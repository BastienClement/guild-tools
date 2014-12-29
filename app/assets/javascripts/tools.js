var GuildTools = angular.module("GuildTools", ["ngRoute", "ngAnimate", "angularMoment"]);
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
		.when("/about", {
			templateUrl: "/assets/views/about.html",
			controller: "AboutCtrl"
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
		.when("/calendar/abs", {
			templateUrl: "/assets/views/absences.html",
			controller: "AbsencesCtrl"
		})
		.when("/calendar/composer", {
			templateUrl: "/assets/views/composer.html",
			controller: "ComposerCtrl"
		})
		.when("/forum", {
			templateUrl: "/assets/views/forum.html",
			controller: "ForumCtrl"
		})
		.when("/roster", {
			templateUrl: "/assets/views/roster.html",
			controller: "RosterCtrl"
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

			if (x + width + 20 > _(window).width()) {
				x -= width + 20;
			}

			if (y + height + 20 > _(window).height()) {
				y -= height + 20;
			}

			tooltip.css({
				left: x,
				bottom: y
			});
		}

		var initDelay = null;

		scope.$on("$destroy", function() {
			if (open) tooltip.hide();
			_(".wowhead-tooltip").hide();
		});

		element.bind("mouseover", function(event) {
			var tooltip_value = attrs.ngTooltip;
			tooltip.css("opacity", 1);

			// Raw text data
			if (tooltip_value[0] === "$") {
				tooltip.text(tooltip_value.slice(1));
			} else {
				var target = _(tooltip_value, element);
				if (!target.length) {
					target = _(tooltip_value);
				}
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
		if (!input) return "";
		return input.charAt(0).toUpperCase() + input.slice(1).toLowerCase();
	};
});

GuildTools.filter("capsfirst", function() {
	return function(input) {
		if (!input) return "";
		return input.charAt(0).toUpperCase() + input.slice(1);
	};
});

GuildTools.filter("markdown", function($sce) {
	var div = _("<div>");
	Showdown.extensions.guildtools = function(converter) {
		return [
			{
				type: "lang",
				regex: "(%){2}([^%]+?)(?:\\((\\d+)\\))?(%){2}",
				replace: function(match, prefix, content, height, suffix) {
					height = height ? " style='height: " + height + "px;'" : "";
					return "<iframe src='" + div.html(content).text() + "' sandbox='allow-scripts'" + height + "></iframe>";
				}
			},
			{
				type: "lang",
				regex: "@([^@]+)\\((\\d+)\\)(:)?@",
				replace: function(match, content, cid, missing) {
					if (missing)
						return "<span class='char c" + cid + "'><i class='awe-attention'></i>" + content + "</span>";
					else
						return "<span class='char c" + cid + "'>" + content + "</span>";
				}
			}
		];
	};
	var markdown = new Showdown.converter({extensions: ["guildtools"]});
	return function(input) {
		input = input.replace(/<iframe.*?src="(https?:\/\/[^"]+?)".*?>.*?<\/iframe>(\(\d+\))/gm, "%%$1$2%%");
		input = input.replace(/<iframe.*?src="(https?:\/\/[^"]+?)".*?>.*?<\/iframe>/gm, "%%$1%%");
		input = div.text(input).html();
		var res = markdown.makeHtml(input);
		res = res.replace(/<a href/g, "<a target='_blank' href");
		return $sce.trustAsHtml(res);
	};
});

GuildTools.directive("scrollGlue", function($parse) {
	return {
		priority: 1,
		restrict: 'A',
		link: function(scope, $el, attrs){
			var el = $el[0];
			var activationState = true;

			scope.$watch(function() {
				if(activationState) el.scrollTop = el.scrollHeight;
			});

			$el.bind('scroll', function() {
				activationState = (el.scrollTop + el.clientHeight + 1 >= el.scrollHeight);
			});
		}
	};
});
