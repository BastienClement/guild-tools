GuildTools.controller("AbsencesCtrl", function($scope) {
	if ($scope.restrict()) return;
	$scope.setNavigator("calendar", "absences");

	$scope.absences = [];
	$scope.upcoming = [];
	$scope.past = [];

	function parse_dates() {
		$scope.absences.forEach(function(abs) {
			if (typeof abs.from == "string") {
				abs.from = new Date(abs.from.replace(" ", "T"));
				abs.to = new Date(abs.to.replace(" ", "T"));
			}
		});
	}

	function format_absences() {
		parse_dates();

		$scope.upcoming = [];
		$scope.past = [];

		var today = new Date();
		today.setHours(0, 0, 0, 0);

		$scope.absences.forEach(function(abs) {
			abs.current = (abs.from < today);
			((abs.to >= today) ? $scope.upcoming : $scope.past).push(abs);
		});
	}

	$scope.setContext("absences:load", null, {
		$: function(data) {
			$scope.absences = data.user;
			format_absences();
		},

		"absence:create": function(abs) {
			$scope.absences.push(abs);
			format_absences();
		},

		"absence:update": function(abs) {
			$scope.absences = $scope.absences.map(function(old) {
				return old.id === abs.id ? abs : old;
			});
			format_absences();
		},

		"absence:delete": function (id) {
			$scope.absences = $scope.absences.filter(function(abs) {
				return abs.id !== id;
			});
			format_absences();
		}
	});

	$scope.createAbsence = function() {
		$scope.modal("absences-edit", { create: true });
	};

	$scope.editAbsence = function(abs) {
		$scope.modal("absences-edit", abs);
	};

	$scope.cancelAbsence = function(abs) {
		if (confirm("Are you sure?")) $.call("absences:cancel", { id: abs.id });
	};

	$scope.formatDate = function(date) {
		return moment(date).format("dddd, D MMMM YYYY");
	};
});

GuildTools.controller("AbsencesEditCtrl", function($scope) {
	var ctx = $scope.ctx = $scope.modalCtx;

	var today = new Date();
	var from = ctx.from && new Date(ctx.from) || today;
	var to = ctx.to && new Date(ctx.to) || today;

	$scope.reason = ctx.reason || "";

	$scope.inflight = false;

	$scope.days = [];
	for (var i = 1; i <= 31; ++i) {
		$scope.days.push({ value: i, label: (i < 10) ? "0" + i : String(i) });
	}

	$scope.months = $scope.monthNames.map(function(month, i) {
		return { value: i, label: month };
	});

	$scope.from = {};
	$scope.to = {};

	$scope.from.day = from.getDate();
	$scope.from.month = from.getMonth();
	$scope.to.day = to.getDate();
	$scope.to.month = to.getMonth();

	function date_watcher(model) {
		return function(new_date) {
			var date = new Date();
			date.setMonth(new_date.month, new_date.day);
			date.setHours(0, 0, 0, 0);

			model.day = date.getDate();
			model.month = date.getMonth();
		};
	}

	$scope.$watch("from", date_watcher($scope.from), true);
	$scope.$watch("to", date_watcher($scope.to), true);

	$scope.save = function() {
		$scope.inflight = true;
		var template = { from: $scope.from, to: $scope.to, reason: $scope.reason};
		if (ctx.create) {
			$.call("absences:create", template, function(err) {
				if (err) return ($scope.inflight = false);
				$scope.modal();
			});
		} else {
			template.id = ctx.id;
			$.call("absences:edit", template, function(err) {
				if (err) return ($scope.inflight = false);
				$scope.modal();
			});
		}
	};
});
