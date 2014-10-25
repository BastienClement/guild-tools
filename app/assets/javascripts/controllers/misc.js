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
	
	$scope.setContext("dashboard:load", null, {
		$: function(data) {
			raw_feed = data.feed;
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
