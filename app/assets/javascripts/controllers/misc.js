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
	$scope.setAutoNavigator("About");
});

GuildTools.controller("SlacksCtrl", function($scope) {
	if ($scope.restrict()) return;
});

GuildTools.controller("ForumCtrl", function($scope) {
	if ($scope.restrict()) return;
});
GuildTools.controller("WhishlistCtrl", function($scope) {
	if ($scope.restrict()) return;
});

GuildTools.controller("BlueprintsCtrl", function($scope) {
	if ($scope.restrict()) return;
});
