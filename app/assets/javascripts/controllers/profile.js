//--------------------------------------------------------------------------------------------------
// Profile Controller
//--------------------------------------------------------------------------------------------------

GuildTools.controller("ProfileCtrl", function($scope, $location, $routeParams) {
	if ($scope.restrict()) return;

	$scope.setNavigator("profile", "main");

	var userid = Number($routeParams.id || $.user.id);
	$scope.editable = (userid === $.user.id);

	$scope.chars = [];

	function update() {
		$scope.user = $.roster.user(userid);
		$scope.chars = $.roster.charsByUser(userid);

		$scope.chars.sort(function(a, b) {
			if (a.main !== b.main) return a.main ? -1 : 1;
			if (a.active !== b.active) return a.active ? -1 : 1;
			return a.name.localeCompare(b.name);
		});
	}

	update();
	$scope.$on("roster-updated", update);

	$scope.setContext("profile:load", { id: userid }, {
		$: function(res) {

		}
	});

	$scope.remove = function(id) {
		$.call("profile:remove", { id: id });
	};

	$scope.enable = function(id) {
		$.call("profile:enable", { id: id });
	};

	$scope.disable = function(id) {
		$.call("profile:disable", { id: id });
	};

	$scope.promote = function(id) {
		$.call("profile:promote", { id: id });
	};

	$scope.role = function(id, role) {
		$.call("profile:role", { id: id, role: role });
	};

	$scope.addChar = function() {
		if ($scope.chars.length >= 15) {
			return $scope.error("You may have a maximum of 15 registered characters with your account at any one time.");
		}
		$scope.modal('player-add-char');
	};

	$scope.canUpdate = function(time) {
		return time >= (Date.now() - 1800000);
	};
});

//--------------------------------------------------------------------------------------------------
// Add new char dialog
//--------------------------------------------------------------------------------------------------

GuildTools.controller("PlayerAddCharCtrl", function($scope, $location) {
	$scope.char = null;
	$scope.loading = false;
	$scope.role = "DPS";

	$scope.setRole = function(role) {
		$scope.role = role;
	};

	function error(text, keepname) {
		$scope.loading = false;
		$scope.error(text);
		if (!keepname) {
			$scope.name = "";
			_("#add-char-name").focus();
		}
	}

	$scope.load = function() {
		if ($scope.loading) return;
		$scope.char = null;
		$scope.loading = true;

		$.bnQuery("character/" + $scope.server + "/" + $scope.name, function(char) {
			if (!char) return error("Unable to load requested character.");
			$.call("profile:check", { server: $scope.server, name: $scope.name }, function(err, available) {
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

		$.call("profile:register", { server: $scope.char.server, char: $scope.char.name, role: $scope.role }, function(err) {
			if (err) return error("An error occured while linking this character to your account.", true);
			if ($scope.embedWelcome) {
				$.user.ready = true;
				$location.path("/dashboard");
			} else {
				$scope.modal();
			}
		});
	};
});
