GuildTools.controller("DashboardCtrl", function($scope, $location) {
	if ($scope.restrict()) return;
	$scope.setNavigator("dashboard", "main");

	$scope.events = [];
	$scope.logs = [];

	$scope.forms = {
		shoutbox: ""
	};

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

			$scope.logs = data.logs;
		},

		"dashboard:feed:update": function(data) {
			raw_feed = data;
			update_feed();
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

		events.sort(function(a, b) {
			if ((a.event.date == b.event.date) && (a.event.type != b.event.type) && (a.event.type == 4 || b.event.type == 4)) {
				return a.event.type == 4 ? -1 : 1;
			}
			return a.timeval - b.timeval;
		});

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

	$scope.formatLogDate = function(date) {
		var time = moment.unix(date);
		return time.format("DD.MM");
	};

	$scope.extractLogTitle = function(title) {
		return title.split(" - ")[0];
	};

	$scope.extractLogUploader = function(title) {
		return title.split(" - ")[1];
	};

	$scope.onlineSort = "name";

	$scope.sendShoutbox = function() {
		$scope.forms.shoutbox = $scope.forms.shoutbox.replace(/^\s+|\s+$/g, "");
		if (!$scope.forms.shoutbox) return;
		$.call("chat:shoutbox:send", { msg: $scope.forms.shoutbox });
		$scope.forms.shoutbox = "";
	};
});
