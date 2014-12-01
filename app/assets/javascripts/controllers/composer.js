GuildTools.controller("ComposerCtrl", function($scope) {
	if ($scope.restrict($scope.isOfficer)) return;
	$scope.setNavigator("calendar", "composer");

	$scope.pickedChar = null;
	$scope.pickedGroup = null;
	$scope.pickedLockout = null;

	$scope.hover = {};

	var lock = false;
	$scope.handleMousemove = function(ev) {
		if (lock) return; else lock = true;
		requestAnimationFrame(function() {
			lock = false;
			_("#composer-char-picker").css({
				top: ev.clientY + 10,
				left: ev.clientX + 10
			});
		});
	};

	$scope.pickChar = function(char, group, lockout) {
		$scope.pickedChar = char;
		$scope.pickedGroup = group;
		$scope.pickedLockout = lockout;
	};

	$scope.dropChar = function() {
		if ($scope.hover.group) {
			if ($scope.pickedGroup) {
				
			}
			$scope.slots.push({ group: $scope.hover.group, char: $scope.pickedChar.id });
		} else {

		}

		$scope.hover = {};
		$scope.pickedChar = null;
		$scope.pickedGroup = null;
		$scope.pickedLockout = null;
	};

	$scope.icons = ["star", "circle", "diamond", "triangle", "moon", "square", "cross", "skull"];
	$scope.hidden = {};

	$scope.toggleHidden = function(lockout) {
		$scope.hidden[lockout] = !$scope.hidden[lockout];
	};

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

	$scope.setContext("composer:load", {}, {
		$: function(data) {
			$scope.lockouts = data.lockouts;
			$scope.groups = data.groups;
			$scope.slots = data.slots;
		},

		"composer:lockout:create": function(lockout) {
			$scope.lockouts.push(lockout);
		},

		"composer:lockout:delete": function(id) {
			$scope.lockouts = $scope.lockouts.filter(function(lockout) {
				return lockout.id != id;
			});

			var removed_groups = {};
			$scope.groups = $scope.groups.filter(function(group) {
				if (group.lockout == id) {
					removed_groups[group.id] = true;
					return false;
				}

				return true;
			});

			$scope.slots = $scope.slots.filter(function(slot) {
				return !removed_groups[slot.group];
			});
		},

		"composer:group:create": function(group) {
			$scope.groups.push(group);
		},

		"composer:group:delete": function(id) {
			$scope.groups = $scope.groups.filter(function(group) {
				return group.id != id;
			});

			$scope.slots = $scope.slots.filter(function(slot) {
				return slot.group != id;
			});
		}
	});

	$scope.deleteLockout = function(id) {
		if (confirm("Are you sure?")) {
			$.call("composer:lockout:delete", {lockout: id});
		}
	};

	$scope.createGroup = function(lockout) {
		$.call("composer:group:create", { lockout: lockout });
	};

	$scope.deleteGroup = function(id) {
		if (confirm("Are you sure?")) {
			$.call("composer:group:delete", {group: id});
		}
	};

	$scope.groupsForLockout = function(lockout) {
		return $scope.groups.filter(function(group) {
			return group.lockout == lockout;
		});
	};

	$scope.charsForGroup = function(group) {
		return $scope.slots.filter(function(slot) {
			return slot.group == group;
		}).map(function(slot) {
			return $.roster.char(slot.char);
		});
	};
});

GuildTools.controller("ComposerNewCtrl", function($scope) {
	$scope.inflight = false;

	$scope.title = "";

	$scope.create = function() {
		$scope.inflight = true;
		$.call("composer:lockout:create", { title: $scope.title }, function() {
			$scope.modal();
		});
	};
});
