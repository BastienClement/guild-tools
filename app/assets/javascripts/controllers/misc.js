GuildTools.controller("WelcomeCtrl", function($scope, $location) {
	if (!$.user) {
		$location.path("/login");
		return;
	}

	$scope.setNavigator();
	$scope.embedWelcome = true;
});

GuildTools.controller("DashboardCtrl", function($scope, $location) {
	if ($scope.restrict()) return;

	$scope.setContext();

	$scope.go = function(path) {
		$location.path(path);
	};

	$scope.setNavigator();
	$scope.onlineSort = "name";
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
