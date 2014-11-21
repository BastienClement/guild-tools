GuildTools.controller("RosterCtrl", function($scope) {
	if ($scope.restrict()) return;

	$scope.setNavigator("roster", "main");

	$scope.formatServer = function(server) {
		return server.replace(/\-/g, " ");
	};
});

GuildTools.controller("RosterSelectorCtrl", function($scope) {
	var ctx = $scope.ctx = $scope.modalCtx;

	$scope.selected = {};
	$scope.filter = "";
	$scope.roster = [];
	$scope.view = [];

	$scope.toggleSelect = function(entry) {
		var id = entry.user.id;
		$scope.selected[id] = !$scope.selected[id];
	};

	$scope.isSelected = function(entry) {
		return !!$scope.selected[entry.user.id];
	};

	$scope.countSelected = function() {
		return Object.keys($scope.selected).filter(selected).length;
	};

	function update_cache() {
		$scope.roster = [];
		$.roster.users.forEach(function(u) {
			$scope.roster.push({
				user: u,
				main: $.roster.mainForUser(u.id),
				chars: $.roster.charsByUser(u.id)
			});
		});
		update_view();
	}

	function update_view() {
		var filter = $scope.filter;

		if (filter) {
			$scope.roster.forEach(function(entry) {
				var chars_scores = Math.max.apply(Math, entry.chars.map(function(char) {
					return fuzzyMatch(filter, char.name);
				}));

				var user_score = fuzzyMatch(filter, entry.main.name);
				entry.score = isFinite(chars_scores) ? Math.max(user_score, chars_scores * 0.9) : user_score;
			});

			$scope.roster.sort(function(a, b) {
				return b.score - a.score;
			});

			var max = $scope.roster[0] ? $scope.roster[0].score : 0;

			$scope.roster.forEach(function(entry) {
				entry.bad = entry.score < 0.75 * Math.pow(max, 2) || entry.score < 0.5;
			});
		} else {
			$scope.roster.forEach(function(entry) {
				entry.bad = false;
			});
		}

		$scope.view = $scope.roster;
	}

	update_cache();
	$scope.$on("roster-updated", update_cache);
	$scope.$watch("filter", update_view);

	function selected(id) { return $scope.selected[id]; }

	$scope.save = function() {
		var list = Object.keys($scope.selected).filter(selected).map(Number);
		if (typeof ctx.cb !== "function") return $scope.modal();
		if (list.length < 1) return ctx.cb(null);
		return ctx.cb(list);
	};
});
