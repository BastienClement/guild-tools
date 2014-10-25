GuildTools.controller("WelcomeCtrl", function($scope, $location) {
	if (!$.user) {
		return $location.path("/login").replace();
	}
	
	if ($.user.ready) {
		return $location.path("/dashboard").replace();
	}

	$scope.setNavigator();
	$scope.embedWelcome = true;
});

GuildTools.controller("AboutCtrl", function($scope) {
	$scope.setNavigator();
});

GuildTools.controller("DashboardCtrl", function($scope, $location) {
	if ($scope.restrict()) return;
	$scope.setNavigator("dashboard", "main");
	
	$scope.events = [];
	
	var raw_feed = [];
	$scope.feed = null;
	$scope.main = $.roster.mainForUser($.user.id);
	
	$scope.$on("roster-updated", function() {
		$scope.main = $.roster.mainForUser($.user.id);
	});
	
	$scope.filters = {
		MMO: true,
		BLUE: true,
		WOW: true
	};
	
	$scope.toggleFilter = function(filter) {
		$scope.filters[filter] = !$scope.filters[filter];
		update_feed();
	};
	
	function filter_feed(feed) {
		//var titles = {};
		return feed.filter(function(entry) {
			if($scope.filters[entry.source]) {
				return true;
			} else {
				return false;
			}
		});
	}
	
	function update_feed() {
		$scope.feed = filter_feed(raw_feed);
	}
	
	function compute_event_time(entry) {
		var date = moment(entry.event.date);
		entry.datestr = date.format("DD.MM");
		
		var time = entry.event.time;
		if (time < 600) date.add(1, "d");
		
		date.hours(Math.floor(time / 100));
		date.minutes(time % 100);
		
		entry.timestr = date.format("HH:mm");
		entry.timeval = date.unix();
	}
	
	$scope.setContext("dashboard:load", null, {
		$: function(data) {
			raw_feed = data.feed;
			update_feed();
			
			$scope.events = [];
			data.events.forEach(function(entry) {
				compute_event_time(entry);
				$scope.events[entry.id] = entry;
			});
		}
	});
	
	$scope.getEntryClasses = function(entry) {
		return (entry.source + " " + entry.tags).toLowerCase();
	};
	
	$scope.getEntryImages = function(entry) {
		switch(entry.source) {
			case "MMO": return ["mmo"];
			case "WOW": return ["wow"];
			case "BLUE":
				var srcs = [entry.tags.match(/EU/) ? "eu" : "us"];
				//if (entry.tags.match(/BLUE/)) srcs.push("blue");
				return srcs;
		}
	};
	
	$scope.formattedEventsList = function() {
		var events = [];
		var now = Date.now() / 1000;
		
		$scope.events.forEach(function(event) {
			events.push(event);
		});
		
		events.sort(function(a, b) { return a.timeval - b.timeval; });
		events = events.filter(function(entry) { return (entry.timeval - now) > -21600 /* 6 hours */; }).slice(0, 7);
		
		var last = "";
		events.forEach(function(event) {
			event._datestr = (last === event.datestr) ? "" : event.datestr;
			last = event.datestr;
		});
		
		return events;
	};
	
	$scope.stateClass = function(e, onlyState) {
		if (!onlyState) {
			if (e.event.type === 4) return "note";
			if (e.event.state === 1) return "closed";
			if (e.event.state === 2) return "canceled";
		}

		switch (e.answer) {
			case 1:
				return "accepted";
			case 2:
				return "declined";
		}
	};

	$scope.onlineSort = "name";
});

GuildTools.controller("SlacksCtrl", function($scope) {
	if ($scope.restrict()) return;
});

GuildTools.controller("ForumCtrl", function($scope) {
	if ($scope.restrict()) return;
});

GuildTools.controller("RosterCtrl", function($scope) {
	if ($scope.restrict()) return;
});

GuildTools.controller("WhishlistCtrl", function($scope) {
	if ($scope.restrict()) return;
});

GuildTools.controller("BlueprintsCtrl", function($scope) {
	if ($scope.restrict()) return;
});
