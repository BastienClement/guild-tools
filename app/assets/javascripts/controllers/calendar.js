//--------------------------------------------------------------------------------------------------
// Calendar Controller
//--------------------------------------------------------------------------------------------------

GuildTools.controller("CalendarCtrl", function($scope) {
	if ($scope.restrict()) return;

	var now = new Date();	
	$scope.month = now.getMonth();
	$scope.year = now.getFullYear();
	
	$scope.setNavigator("calendar", "main");

	$scope.monthNext = function() { 
		$scope.month += 1;
		if ($scope.month > 11) {
			$scope.month = 0;
			$scope.year += 1;
		}
		$scope.buildCalendar();
	};

	$scope.monthPrev = function() {
		$scope.month -= 1;
		if ($scope.month < 0) {
			$scope.month = 11;
			$scope.year -= 1;
		}
		$scope.buildCalendar();
	};

	$scope.monthCurrent = function() {
		var now = new Date();
		if ($scope.month === now.getMonth() && $scope.year === now.getFullYear()) return;
		$scope.month = now.getMonth();
		$scope.year = now.getFullYear();
		$scope.buildCalendar();
	};

	function zero_pad(i) {
		return (i < 10) ? "0" + i : "" + i;
	}

	$scope.data = [];
	$scope.events = {};

	function pushEvent(event) {
		if (!$scope.events[event.date]) {
			$scope.events[event.date] = [event];
		} else {
			$scope.events[event.date].push(event);
		}
	}

	$scope.buildCalendar = function() {
		/*$scope.setContext("calendar:main", [$scope.month + 1, $scope.year], {
			$: function(events) {
				$scope.events = {};
				events.forEach(pushEvent);
			},

			EventCreated: function(event) {
				pushEvent(event);
			},

			EventDeleted: function(eventid) {
				for (var day in $scope.events) {
					for (var i in $scope.events[day]) {
						var event = $scope.events[day][i];
						if (event.id === eventid) {
							$scope.events[day].splice(i, 1);
							return;
						}
					}
				}
			},

			AnswerUpdated: function(eventid, answer) {
				for (var day in $scope.events) {
					for (var i in $scope.events[day]) {
						var event = $scope.events[day][i];
						if (event.id === eventid) {
							event.answer = answer;
							return;
						}
					}
				}
			}
		});*/

		var last_month = new Date($scope.year, $scope.month, 0);
		var days_in_last_month = last_month.getDate();
		var last_month_id = last_month.getMonth();
		var last_month_year = last_month.getFullYear();

		var next_month = new Date($scope.year, $scope.month + 1, 1);
		var next_month_id = next_month.getMonth();
		var next_month_year = next_month.getFullYear();

		var days_in_month = (new Date($scope.year, $scope.month + 1, 0)).getDate();
		var first_month_day = ((new Date($scope.year, $scope.month, 1).getDay()) + 6) % 7;

		var today = new Date();

		for(var r = 0; r < 6; r++) {
			if(!$scope.data[r]) $scope.data[r] = [];

			for(var c = 0; c < 7; c++) {
				if(!$scope.data[r][c]) $scope.data[r][c] = {};

				var day = 7 * r + c - first_month_day + 1;
				var month = $scope.month;
				var year = $scope.year;

				if(day < 1) {
					day = days_in_last_month + day;
					month = last_month_id;
					year = last_month_year;
				} else if(day > days_in_month) {
					day = day - days_in_month;
					month = next_month_id;
					year = next_month_year;
				}

				var cell = $scope.data[r][c];
				cell.day = day;
				cell.month = month + 1;
				cell.year = year;
				cell.inactive = (month != $scope.month);
				cell.today = (year == today.getFullYear() && month == today.getMonth() && day == today.getDate());
				cell.id = year + "-" + zero_pad(month + 1) + "-" + zero_pad(day);
			}
		}
	};

	$scope.buildCalendar();

	$scope.open = function(id) {
		$scope.breadcrumb.push("/calendar/event/" + id);
	};

	$scope.accept = function(id, ev) {
		ev.stopPropagation();
		$.call("answerEvent", id, 1, null);
	};

	$scope.decline = function(id, ev) {
		ev.stopPropagation();
		$.call("answerEvent", id, 2, null, null);
	};

	$scope.eventMenu = function(event, ev) {
		var menu = [];

		var hasDelete = false;
		var hasControls = false;

		if (event.owner === $.user.id || $.user.officer) {
			hasDelete = true;
			menu.push({
				icon: "awe-trash", text: "Delete", action: function() {
					if (confirm("Are you sure?"))
						$.call("deleteEvent", event.id);
				}, order: 10
			});
		}

		if (event.type !== 4) {
			menu.push({
				icon: "awe-link-ext-alt", text: "Open", action: function() {
					$scope.open(event.id);
				}, order: 1
			});

			if (event.state === 0) {
				hasControls = true;
				menu.push({
					icon: "awe-ok", text: "Accept", action: function() {
						$scope.accept(event.id, ev);
					}, order: 3
				});

				menu.push({
					icon: "awe-cancel", text: "Decline", action: function() {
						$scope.decline(event.id, ev);
					}, order: 4
				});
			}
		}

		if (hasDelete && hasControls) {
			menu.push({ separator: true, order: 5 });
		}

		if (menu.length) {
			$scope.menu(menu, ev);
		}
	};

	$scope.formatTime = function(time) {
		var str = time.toString();
		return str.slice(0,-2) + ':' + str.slice(-2);
	};

	$scope.typeLabel = function(t) {
		switch (t) {
		case 1: return "Guild event";
		case 2: return "Public event";
		case 3: return "Private event";
		case 4: return "Announce";
		}
	};

	$scope.stateClass = function(e, onlyState) {
		if (!onlyState) {
			if (e.type === 4) return "note";
			if (e.state === 1) return "closed";
			if (e.state === 2) return "canceled";
		}

		switch (e.answer) {
		case 1: return "accepted";
		case 2: return "declined";
		}
	};

	$scope.stateLabel = function(a) {
		switch (a) {
		case 1: return "Accepted";
		case 2: return "Declined";
		default: return "Non register";
		}
	};
});

//--------------------------------------------------------------------------------------------------
// Add event controller
//--------------------------------------------------------------------------------------------------

GuildTools.controller("CalendarAddEventCtrl", function($scope) {
	if ($scope.restrict()) return;
	
	$scope.setNavigator("calendar", "main");

	$scope.working = false;
	$scope.step = 1;

	$scope.eventTitle = "";
	$scope.eventDesc = "";
	$scope.eventType = 1;
	$scope.eventHour = 0;
	$scope.eventMin = 0;

	var now = new Date();
	var next;
	if (now.getMonth() == 11) {
		next = new Date(now.getFullYear() + 1, 0, 1);
	} else {
		next = new Date(now.getFullYear(), now.getMonth() + 1, 1);
	}

	function zero_pad(i) {
		return (i < 10) ? "0" + i : "" + i;
	}

	function buildCalendar(date) {
		var year = date.getFullYear();
		var month = date.getMonth();
		var days_in_month = (new Date(year, month + 1, 0)).getDate();
		var first_month_day = ((new Date(year, month, 1).getDay()) + 6) % 7;

		var today = new Date();
		var calendar = [[]];
		var cur_line = 0;

		function pushDay(day) {
			if (calendar[cur_line].length > 6) {
				++cur_line;
				calendar[cur_line] = [];
			}

			calendar[cur_line].push(day);
		}

		for (var d = 0; d < first_month_day; ++d) {
			pushDay({ old: true, text: "" });
		}

		for (d = 0; d < days_in_month; ++d) {
			pushDay({
				old: (year <= today.getFullYear() && month <= today.getMonth() && (d+1) < today.getDate()),
				today: (year == today.getFullYear() && month == today.getMonth() && (d+1) == today.getDate()),
				id: year + "-" + zero_pad(month + 1) + "-" + zero_pad(d + 1),
				text: (d + 1)
			});
		}

		return calendar;
	}

	$scope.thisMonth = buildCalendar(now);
	$scope.thisMonthName = $scope.monthNames[now.getMonth()];
	$scope.nextMonth = buildCalendar(next);
	$scope.nextMonthName = $scope.monthNames[next.getMonth()];

	var selected = [];
	var lastClick = null;

	$scope.isSelected = function(id) {
		return selected.some(function(d) { return d === id; });
	};

	$scope.select = function(ev, id) {
		ev.preventDefault();

		if ($scope.isSelected(id)) {
			selected = selected.filter(function(d) { return d !== id; });
			lastClick = null;
			return;
		}

		function next(date) {
			return new Date(date.getFullYear(), date.getMonth(), date.getDate() + 1);
		}

		function toId(date) {
			return date.getFullYear() + "-" + zero_pad(date.getMonth() + 1) + "-" + zero_pad(date.getDate());
		}

		if (ev.ctrlKey && $.user.officer) {
			selected.push(id);
		} else if(ev.shiftKey && lastClick && $.user.officer) {
			if (id === lastClick) return;
			var d1 = new Date(id);
			var d2 = new Date(lastClick);

			var from = d1 < d2 ? d1 : d2;
			var to = d1 < d2 ? d2 : d1;

			var day = from;
			var end = toId(to);

			for (var i = 0;; ++i) {
				if (i > 62) {
					selected = [];
					break;
				}

				var dayid = toId(day);

				if (!$scope.isSelected(dayid)) {
					selected.push(dayid);
				}

				if (dayid == end) {
					break;
				}

				day = next(day);
			}
		} else {
			selected = [id];
		}

		lastClick = id;
	};

	$scope.canContinue = function() {
		return selected.length > 0;
	};

	$scope.nextStep = function() {
		$scope.step = 2;
		setTimeout(function() {
			_("#calendar-add-event-title").focus();
		}, 200);

		$scope.eventTitle = "";
		$scope.eventDesc = "";
		$scope.eventType = $.user.officer ? 1 : 3;

		$scope.eventHour = 0;
		$scope.eventMin = 0;
		$scope.clockMinutes = false;
	};

	$scope.clockMinutes = false;

	$scope.setHour = function(hour) {
		$scope.eventHour = hour;
		$scope.clockMinutes = true;
	};

	$scope.setMinutes = function(min) {
		$scope.eventMin = min;
		_("#calendar-add-event-title").focus();
	};

	$scope.backToHours = function() {
		$scope.clockMinutes = false;
	};

	$scope.doCreate = function() {
		$scope.working = true;
		$.call("createEvent", {
			title: $scope.eventTitle,
			desc: $scope.eventDesc,
			type: $scope.eventType,
			hour: $scope.eventHour,
			min: $scope.eventMin,
			dates: selected
		}, function(err) {
			$scope.working = false;
			if (!err) $scope.modal();
		});
	};
});

//--------------------------------------------------------------------------------------------------
// Event Controller
//--------------------------------------------------------------------------------------------------

GuildTools.controller("CalendarEventCtrl", function($scope, $location, $routeParams) {
	if ($scope.restrict()) return;
	var eventid = Number($routeParams.id);

	$scope.event = null;
	$scope.note = null;
	$scope.answers = [];
	$scope.tab_selected = 1;

	var cached_tab;
	var cached_groups;

	$scope.answer = 1;
	$scope.answer_note = "";

	var raw_answers;

	function build_answers() {
		$scope.answers = [];
		cached_tab = null;

		raw_answers.forEach(function(answer) {
			if (!answer.chars.length) {
				answer.main = {
						name: answer.username,
						main: 1,
						"class": 99,
						role: "UNKNOW"
				};
			} else {
				answer.chars.some(function(char) {
					if ((!answer.main && char.main) || char.id === answer.char) {
						answer.main = char;
						return true;
					}
				});
			}

			if (!$scope.answers[answer.answer]) {
				$scope.answers[answer.answer] = [answer];
			} else {
				$scope.answers[answer.answer].push(answer);
			}
		});
	}

	$scope.setContext("calendar:event", [eventid], {
		$: function(event, answers) {
			if (!event) {
				$scope.error("This event is not available");
				$location.path("/calendar").replace();
				return;
			}

			if (event.answer) {
				$scope.answer = event.answer;
				$scope.answer_note = event.answer_note;
			}

			$scope.breadcrumb.override({ name: event.title });
			$scope.event = event;
			$scope.note = event.note;

			raw_answers = answers;
			build_answers();
		},

		AnswerUpdated: function(answer) {
			if (answer.user === $.user.id) {
				$scope.answer = answer.answer;
				$scope.answer_note = answer.note;
			}

			raw_answers = raw_answers.map(function(a) {
				if (a.user === answer.user) {
					return answer;
				} else {
					return a;
				}
			});

			build_answers();
		}
	});

	function zeroPad(n) {
		return String((n < 10) ? "0" + n : n);
	}

	$scope.formatDate = function(d) {
		var date = new Date(d);
		date = new Date(date.getTime() + date.getTimezoneOffset() * 60000);

		var day = zeroPad(date.getDate()) + "/" + zeroPad(date.getMonth() + 1) + "/" + date.getFullYear();
		var hour = zeroPad(date.getHours()) + ":" + zeroPad(date.getMinutes());
		return day + " - " + hour;
	};

	$scope.getAnswersGroups = function(tab) {
		if (cached_tab === tab) {
			return cached_groups;
		}

		var groups = [];
		var group = [];

		if (!$scope.answers[tab]) return [];

		$scope.answers[tab].sort(function(a, b) {
			a = a.main;
			b = b.main;
			if (a["class"] !== b["class"]) return a["class"] - b["class"];
			return a.name.localeCompare(b.name);
		});

		$scope.answers[tab].forEach(function(answer) {
			group.push(answer);
			if (group.length >= 2) {
				groups.push(group);
				group = [];
			}
		});

		if (group.length) {
			if (group.length < 2) {
				group.push({ void: true });
			}

			groups.push(group);
		}

		cached_tab = tab;
		cached_groups = groups;

		return groups;
	};

	$scope.inflight = false;
	$scope.updateAnswer = function(answer, note, char) {
		char = char ? Number(char) : null;
		$scope.inflight = true;
		$.call("answerEvent", eventid, Number(answer), note, char, function() {
			$scope.inflight = false;
		});
	};

	$scope.active_tab = "Raid";
	$scope.tabs = [{ title: "Raid" }];

	$scope.eventTimeFormat = function() {
		if (!$scope.event) return "00:00";
		var time = $scope.event.time;
		time = (time < 1000) ? "0" + time : String(time);
		return time.slice(0, 2) + ":" + time.slice(2);
	};

	$scope.eventStateFormat = function() {
		if (!$scope.event) return "Unknown";
		switch ($scope.event.state) {
		case 0: return "Open";
		case 1: return "Locked";
		case 2: return "Canceled";
		}
	};

	$scope.eventEditable = function() {
		if (!$scope.event) return false;
		return $scope.event.owner === $.user.id || $.user.officer;
	};

	$scope.eventMenu = function(ev) {
		var menu = [
			{
				icon: "awe-lock-open-alt", text: "Open", action: function() {
					//$.call("deleteEvent", event.id);
				}, order: 1
			},
			{
				icon: "awe-lock", text: "Lock", action: function() {
					//$.call("deleteEvent", event.id);
				}, order: 2
			},
			{
				icon: "awe-cancel", text: "Cancel", action: function() {
					//$.call("deleteEvent", event.id);
				}, order: 3
			},
			{ separator: true, order: 10 },
			{
				icon: "awe-trash", text: "Delete", action: function() {
					if (confirm("Are you sure?"))
						$.call("deleteEvent", event.id);
				}, order: 11
			}
		];

		$scope.menu(menu, ev);
	};

	$scope.raidbuffs = {
			stats: [1126, false],
			stamina: [21562, false],
			ap: [19506, false],
			sp: [1459, false],
			crit: [116781, false],
			haste: [116956, false],
			mastery: [19740, false],
			multistrike: [166916, false],
			versatility: [167187, false]
	};

	var buffs_table = [
		{ b: "stats", c: 11, w: 1126, i: "stats_druid" },
		{ b: "stats", c: 10, w: 115921, i: "stats_monk" },
		{ b: "stats", c: 2, opt: true, wh: 20217, i: "stats_paladin_opt" },

		{ b: "stamina", c: 5, w: 21562, i: "stamina_priest" },
		{ b: "stamina", c: 1, opt: true, w: 6673, i: "stamina_warrior" },
		{ b: "stamina", c: 9, opt: true, w: 166928, i: "stamina_warlock_opt" },

		{ b: "ap", c: 3, w: 19506, i: "ap_hunt" },
		{ b: "ap", c: 6, w: 57330, i: "ap_dk" },
		{ b: "ap", c: 1, opt: true, w: 6673, i: "ap_warrior" },

		{ b: "sp", c: 8, w: 1459, i: "sp_mage" },
		{ b: "sp", c: 9, w: 109773, i: "sp_warlock" },

		{ b: "crit", c: 10, r: "TANK", w: 116781, i: "crit_monk_nonheal" },
		{ b: "crit", c: 10, r: "DPS", w: 116781, i: "crit_monk_nonheal" },
		{ b: "crit", c: 8, w: 1459, i: "crit_mage" },

		{ b: "haste", c: 5, r: "DPS", w: 49868, i: "haste_priest_dps" },
		{ b: "haste", c: 4, w: 113742, i: "haste_rogue" },
		{ b: "haste", c: 7, w: 116956, i: "haste_shaman" },
		{ b: "haste", c: 6, r: "DPS", w: 55610, i: "haste_dk_dps" },

		{ b: "mastery", c: 7, w: 116956, i: "mastery_shaman" },
		{ b: "mastery", c: 6, r: "TANK", w: 155522, i: "mastery_dk_tank" },
		{ b: "mastery", c: 2, opt: true, w: 19740, i: "mastery_paladin_opt" },

		{ b: "multistrike", c: 10, r: "DPS", w: 166916, i: "multistrike_monk_dps" },
		{ b: "multistrike", c: 9, w: 109773, i: "multistrike_warlock" },
		{ b: "multistrike", c: 4, w: 113742, i: "multistrike_rogue" },
		{ b: "multistrike", c: 5, r: "DPS", w: 49868, i: "multistrike_priest_dps" },

		{ b: "versatility", c: 2, r: "DPS", w: 167187, i: "versatility_paladin_dps" },
		{ b: "versatility", c: 1, r: "DPS", w: 167188, i: "versatility_warrior_dps" },
		{ b: "versatility", c: 6, r: "DPS", w: 55610, i: "versatility_dk_dps" },
		{ b: "versatility", c: 11, w: 1126, i: "versatility_druid" },
	];

	$scope.getBuffIcon = function(key) {
		if ($scope.raidbuffs[key][1]) {
			return "/assets/buffs/" + $scope.raidbuffs[key][1] + ".jpg";
		} else {
			return "/assets/buffs/" + key + ".jpg";
		}
	};

	$scope.computeRaidBuffs = function() {
		$scope.raidbuffs.stamina = [166928, "stamina_warlock_opt"];
		$scope.raidbuffs.sp = [109773, "sp_warlock"];
		$scope.raidbuffs.multistrike = [109773, "multistrike_warlock"];
	};

	$scope.computeRaidBuffs();
});