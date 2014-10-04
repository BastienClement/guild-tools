//--------------------------------------------------------------------------------------------------
// Profile Controller
//--------------------------------------------------------------------------------------------------

GuildTools.controller("ProfileCtrl", function($scope, $location, $routeParams) {
	if ($scope.restrict()) return;

	var userid = Number($routeParams.id || $.user.id);
	$scope.editable = (userid === $.user.id);

	$scope.profile = {};
	$scope.chars = [];

	$scope.setContext("profile:load", [userid], {
		$: function(res) {
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

//--------------------------------------------------------------------------------------------------
// Add new char dialog
//--------------------------------------------------------------------------------------------------

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