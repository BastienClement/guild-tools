GuildTools.controller("ComposerCtrl", function($scope) {
	if ($scope.restrict()) return;
	$scope.setNavigator("calendar", "composer");

	function build_roster() {
		var mains = [];
		var alts = [];
		var roster = $scope.roster = [];

		$.roster.chars.forEach(function(char) {
			(char.main ? mains : alts).push(char);
		});

		function sort_group(use_ilvl) {
			return function(a, b) {
				if (a.role != b.role) return a.role > b.role ? -1 : 1;
				if (use_ilvl && a.ilvl != b.ilvl) return a.ilvl > b.ilvl ? -1 : 1;
				return a.name > b.name ? 1 : -1;
			};
		}

		mains.sort(sort_group(false));
		roster.push({ title: "Mains", chars: mains });

		var alt_groups = [
			{ title: "Alts 685+", ilvl: 685, chars: [] },
			{ title: "Alts 670+", ilvl: 670, chars: [] },
			{ title: "Alts 655+", ilvl: 655, chars: [] },
			{ title: "Alts 640+", ilvl: 640, chars: [] },
			{ title: "Alts 630+", ilvl: 630, chars: [] },
			{ title: "Alts 615+", ilvl: 615, chars: [] },
			{ title: "Alts 600+", ilvl: 600, chars: [] },
			{ title: "Alts crappy", ilvl: 0, chars: [] }
		];

		alts.forEach(function(char) {
			if (!char.active) return;
			alt_groups.find(function(group) {
				return char.ilvl >= group.ilvl;
			}).chars.push(char);
		});

		alt_groups.filter(function(group) {
			return group.chars.length > 0;
		}).forEach(function(group) {
			group.chars.sort(sort_group(true));
			roster.push(group);
		});
	}

	build_roster();
	$scope.$on("roster-updated", build_roster);
});
