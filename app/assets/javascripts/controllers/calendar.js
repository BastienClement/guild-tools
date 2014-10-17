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

	function zero_pad(i, length) {
		var istr = String(i);
		while (istr.length < length) {
			istr = "0" + istr;
		}
		return istr;
	}

	$scope.data = [];
	$scope.events = {};
	$scope.answers = {};

	function pushEvent(event) {
		var date = event.date.split(" ")[0];
		
		event.sortTime = (event.time < 600) ? event.time + 2400 : event.time;
		event.time = zero_pad(event.time, 3);
		
		if (event.type === 4) {
			event.sortTime -= 3000;
		}
		
		if (!$scope.events[date]) {
			$scope.events[date] = [event];
		} else {
			$scope.events[date].push(event);
		}
	}

	$scope.buildCalendar = function() {
		$scope.setContext("calendar:load", { month: $scope.month, year: $scope.year }, {
			$: function(entries) {
				$scope.events = {};
				$scope.answers = {};
				entries.forEach(function(entry) {
					pushEvent(entry.event);
					$scope.answers[entry.id] = entry.answer;
				});
			},

			"event:create": function(event) {
				pushEvent(event);
			},
			
			"event:update": function(new_event) {
				for (var day in $scope.events) {
					for (var i in $scope.events[day]) {
						var event = $scope.events[day][i];
						if (event.id === new_event.id) {
							$scope.events[day][i] = new_event;
							return;
						}
					}
				}
			},

			"event:delete": function(eventid) {
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

			"answer:create": function(answer) {
				$scope.answers[answer.event] = answer.answer;
			},

			"answer:update": function(answer) {
				$scope.answers[answer.event] = answer.answer;
			}
		});

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

		for (var r = 0; r < 6; r++) {
			if (!$scope.data[r]) $scope.data[r] = [];

			for (var c = 0; c < 7; c++) {
				if (!$scope.data[r][c]) $scope.data[r][c] = {};

				var day = 7 * r + c - first_month_day + 1;
				var month = $scope.month;
				var year = $scope.year;

				if (day < 1) {
					day = days_in_last_month + day;
					month = last_month_id;
					year = last_month_year;
				} else if (day > days_in_month) {
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
				cell.id = year + "-" + zero_pad(month + 1, 2) + "-" + zero_pad(day, 2);
			}
		}
	};

	$scope.buildCalendar();

	$scope.open = function(id, type) {
		if (type === 4) return;
		$scope.breadcrumb.push("/calendar/event/" + id);
	};

	$scope.accept = function(id, ev) {
		ev.stopPropagation();
		$.call("calendar:answer", { event: id, answer: 1 });
	};

	$scope.decline = function(id, ev) {
		ev.stopPropagation();
		$.call("calendar:answer", { event: id, answer: 2 });
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
						$.call("calendar:delete", { id: event.id });
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
		return str.slice(0, -2) + ':' + str.slice(-2);
	};

	$scope.typeLabel = function(t) {
		switch (t) {
			case 1:
				return "Guild event";
			case 2:
				return "Public event";
			case 3:
				return "Private event";
			case 4:
				return "Announce";
		}
	};

	$scope.stateClass = function(e, onlyState) {
		if (!onlyState) {
			if (e.type === 4) return "note";
			if (e.state === 1) return "closed";
			if (e.state === 2) return "canceled";
		}

		switch ($scope.answers[e.id]) {
			case 1:
				return "accepted";
			case 2:
				return "declined";
		}
	};

	$scope.stateLabel = function(a) {
		switch (a) {
			case 1:
				return "Accepted";
			case 2:
				return "Declined";
			default:
				return "Non register";
		}
	};
});

// --------------------------------------------------------------------------------------------------
// Add event controller
// --------------------------------------------------------------------------------------------------

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
		var calendar = [
			[]
		];
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
				old: (year <= today.getFullYear() && month <= today.getMonth() && (d + 1) < today.getDate()),
				today: (year == today.getFullYear() && month == today.getMonth() && (d + 1) == today.getDate()),
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
		return selected.some(function(d) {
			return d === id;
		});
	};

	$scope.select = function(ev, id) {
		ev.preventDefault();

		if ($scope.isSelected(id)) {
			selected = selected.filter(function(d) {
				return d !== id;
			});
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
		} else if (ev.shiftKey && lastClick && $.user.officer) {
			if (id === lastClick) return;
			var d1 = new Date(id);
			var d2 = new Date(lastClick);

			var from = d1 < d2 ? d1 : d2;
			var to = d1 < d2 ? d2 : d1;

			var day = from;
			var end = toId(to);

			for (var i = 0; ; ++i) {
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
		$.call("calendar:create", {
			title: $scope.eventTitle,
			desc: $scope.eventDesc,
			type: Number($scope.eventType),
			hour: $scope.eventHour,
			min: $scope.eventMin,
			dates: selected
		}, function(err) {
			$scope.working = false;
			if (!err) $scope.modal();
		});
	};
});

// --------------------------------------------------------------------------------------------------
// Event Controller
// --------------------------------------------------------------------------------------------------

GuildTools.controller("CalendarEventCtrl", function($scope, $location, $routeParams) {
	if ($scope.restrict()) return;
	var eventid = Number($routeParams.id);
	
	$scope.setNavigator("calendar", "event");

	$scope.event = null;
	$scope.answers = [];
	$scope.chars = {};
	$scope.tabs = [];
	$scope.slots = {};
	$scope.tab_selected = 0;
	$scope.editable = false;
	
	$scope.setTab = function(t) {
		$scope.tab_selected = t;
	};

	var cached_tab;
	var cached_groups;
	$scope.roster_tab = 1;

	$scope.answer = 1;
	$scope.answer_note = "";

	var raw_answers;
	
	$scope.picked = null;
	$scope.pickedFromSlot = false;
	$scope.setPicker = function(char, ev, slot) {
		if (!char || !$scope.editable) return false;
		$scope.picked = char;
		$scope.pickedFromSlot = slot;
		$scope.handlePicker(ev);
		ev.preventDefault();
		setTimeout(function() {
			$scope.handlePicker(ev);
		}, 100);
	};
	
	function filterChar(char) {
		return {
			owner: char.owner,
			name: char.name,
			"class": char["class"],
			role: char.role
		};
	}
	
	$scope.dropPicker = function() {
		if ($scope.pickerTarget === $scope.pickedFromSlot || !$scope.picked) {
			$scope.picked = null;
			return;
		}
		
		var template = {
			tab: $scope.tab_selected,
		};
		
		if (!$scope.pickerTarget) {
			if ($scope.pickedFromSlot) {
				template.slot = $scope.pickedFromSlot;
				$.call("calendar:comp:reset", template);
			}
		} else {
			if ($scope.pickerTargetChar && $scope.pickedFromSlot) {
				template.slot = $scope.pickedFromSlot;
				template.char = filterChar($scope.pickerTargetChar);
				$.call("calendar:comp:set", template);
			}
			template.slot = $scope.pickerTarget;
			template.char = filterChar($scope.picked);
			$.call("calendar:comp:set", template);
		}
		
		$scope.picked = null;
	};
	
	$scope.pickerTarget = null;
	$scope.pickerTargetChar = null;
	$scope.setPickerTarget = function(slot, char) {
		$scope.pickerTarget = slot;
		$scope.pickerTargetChar = char;
	};
	
	var lock = false;
	$scope.handlePicker = function(ev) {
		if (lock) return; else lock = true;
		requestAnimationFrame(function() {
			lock = false;
			_("#calendar-char-picker").css({
				top: ev.clientY + 10,
				left: ev.clientX + 10
			});
		});
	};

	function extract_main(data) {
		return function(char) {
			if ((!data.main && char.main) || char.id === data.answer.char) {
				data.main = char;
				return true;
			}
		};
	}
	
	function build_answers() {
		$scope.answers = [];
		$scope.chars = {};
		cached_tab = null;

		for (var id in raw_answers) {
			var data = raw_answers[id];
			
			if (!data.answer) {
				data.answer = { answer: 0 };
			}
			
			if (!data.chars.length) {
				data.main = {
					name: data.user.name,
					main: 1,
					"class": 99,
					role: "UNKNOW"
				};
			} else {
				data.chars.some(extract_main(data));
			}

			if (!$scope.answers[data.answer.answer]) {
				$scope.answers[data.answer.answer] = [data];
			} else {
				$scope.answers[data.answer.answer].push(data);
			}
			
			$scope.chars[id] = data.chars;
		}
	}

	$scope.setContext("calendar:event", { id: eventid }, {
		$: function(data) {
			if (data.answer) {
				$scope.answer = data.answer.answer;
				$scope.answer_note = data.answer.note;
			}

			$scope.breadcrumb.override({ name: data.event.title });
			$scope.event = data.event;
			$scope.tabs = data.tabs;
			$scope.tab_selected = data.tabs[0].id;
			$scope.slots = data.slots;
			$scope.editable = data.editable;

			raw_answers = data.answers;
			build_answers();
		},
		
		"event:update": function(data) {
			$scope.event = data;
		},
		
		"event:update:full": function(data) {
			$scope.event = data.event;
			$scope.tabs = data.tabs;
			$scope.tab_selected = data.tabs[0].id;
			$scope.slots = data.slots;
		},
		
		"event:delete": function(data) {
			$scope.breadcrumb.push("/calendar");
			$scope.error("Event deleted");
		},

		"answer:replace": function(data) {
			if (data.user.id === $.user.id) {
				$scope.answer = data.answer.answer;
				$scope.answer_note = data.answer.note;
			}

			raw_answers[data.user.id] = data;
			build_answers();
		},
		
		"calendar:slot:update": function(data) {
			var comp = $scope.slots[data.tab];
			if (!comp) {
				comp = $scope.slots[data.tab] = {};
			}
			
			// Remove old entry
			for (var slot in comp) {
				var char = comp[slot];
				if (char && char.owner == data.owner) {
					delete comp[slot];
				}
			}
			
			// Add new entry
			comp[data.slot] = data;
		},
		
		"calendar:slot:delete": function(data) {
			var comp = $scope.slots[data.tab];
			if (comp) delete comp[data.slot];
		},
		
		"calendar:tab:create": function(tab) {
			$scope.tabs.push(tab);
		},
		
		"calendar:tab:update": function(tab_new) {
			$scope.tabs = $scope.tabs.map(function(tab) {
				return (tab.id === tab_new.id) ? tab_new : tab;
			});
		},
		
		"calendar:tab:delete": function(id) {
			$scope.tabs = $scope.tabs.filter(function(tab) {
				return tab.id !== id;
			});
		},
		
		"calendar:tab:wipe": function(id) {
			delete $scope.slots[id];
		}
	});

	function zero_pad(i, length) {
		var istr = String(i);
		while (istr.length < length) {
			istr = "0" + istr;
		}
		return istr;
	}

	$scope.formatDate = function(d) {
		var date = new Date(d);
		/*date = new Date(date.getTime() + date.getTimezoneOffset() * 60000);*/

		var day = zero_pad(date.getDate(), 2) + "/" + zero_pad(date.getMonth() + 1, 2) + "/" + date.getFullYear();
		var hour = zero_pad(date.getHours(), 2) + ":" + zero_pad(date.getMinutes(), 2);
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
		$.call("calendar:answer", { event: eventid, answer: Number(answer), note: note, char: char }, function() {
			$scope.inflight = false;
		});
	};

	$scope.eventTimeFormat = function() {
		if (!$scope.event) return "00:00";
		var time = zero_pad(String($scope.event.time), 4);
		return time.slice(0, 2) + ":" + time.slice(2);
	};

	$scope.eventStateFormat = function() {
		if (!$scope.event) return "Unknown";
		switch ($scope.event.state) {
			case 0:
				return "Open";
			case 1:
				return "Locked";
			case 2:
				return "Canceled";
		}
	};

	$scope.eventMenu = function(ev) {
		var menu = [
			{
				icon: "awe-lock-open-alt",
				text: "Open",
				action: function() {
					$.call("calendar:event:state", { state: 0 });
				},
				order: 1
			},
			{
				icon: "awe-lock",
				text: "Lock",
				action: function() {
					$.call("calendar:event:state", { state: 1 });
				},
				order: 2
			},
			{
				icon: "awe-cancel",
				text: "Cancel",
				action: function() {
					$.call("calendar:event:state", { state: 2 });
				},
				order: 3
			},
			{ separator: true, order: 10 },
			{
				icon: "awe-docs",
				text: "Import answers",
				action: function() {
					
				},
				order: 11
			},
			{
				icon: "awe-trash",
				text: "Delete",
				action: function() {
					if (confirm("Are you sure?"))
						$.call("calendar:delete", { id: $scope.event.id });
				},
				order: 12
			}
		];

		$scope.menu(menu, ev);
	};
	
	$scope.tabMenu = function(tab, ev) {
		if (!$scope.editable) return;
		
		function greaterThan(x) {
			return function(a) { return a.order > x.order; };
		}
		
		function lesserThan(x) {
			return function(a) { return a.order < x.order; };
		}
		
		function max(n, b) { return (b.order > n) ? b.order : n; }
		function min(n, b) { return (b.order < n || n < 0) ? b.order : n; }
		
		function extract(order) {
			return $scope.tabs.reduce(function(a, b) {
				return a || (b.order === order && b) || null;
			}, null);
		}
		
		var menu = [
			{
				icon: "awe-pencil",
				text: "Rename",
				action: function() {
					$scope.modal("calendar-rename-tab", tab);
				},
				order: 0
			},
			{
				icon: "awe-flash",
				text: "Wipe tab",
				action: function() {
					if (confirm("Are you sure?"))
						$.call("calendar:tab:wipe", { id: tab.id });
				},
				order: 0
			},
			{ separator: true, order: 0.5, visible: $scope.tabs.length > 1 },
			{
				icon: "awe-left-dir",
				text: "Move left",
				action: function() {
					var target = extract($scope.tabs.filter(lesserThan(tab)).reduce(max, -1));
					$.call("calendar:tab:swap", { a: target.id, b: tab.id });
				},
				order: 1,
				visible: $scope.tabs.filter(lesserThan(tab)).length > 0
			},
			{
				icon: "awe-right-dir",
				text: "Move right",
				action: function() {
					var target = extract($scope.tabs.filter(greaterThan(tab)).reduce(min, -1));
					$.call("calendar:tab:swap", { a: target.id, b: tab.id });
				},
				order: 2,
				visible: $scope.tabs.filter(greaterThan(tab)).length > 0
			},
			{ separator: true, order: 10, visible: !tab.locked },
			{
				icon: "awe-trash",
				text: "Delete",
				action: function() {
					if (confirm("Are you sure?"))
						$.call("calendar:tab:delete", { id: tab.id });
				},
				order: 11, visible: !tab.locked
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
		{ b: "versatility", c: 11, w: 1126, i: "versatility_druid" }
	];

	$scope.getBuffIcon = function(key) {
		if ($scope.raidbuffs[key][1]) {
			return "/assets/images/buffs/" + $scope.raidbuffs[key][1] + ".jpg";
		} else {
			return "/assets/images/buffs/" + key + ".jpg";
		}
	};

	$scope.computeRaidBuffs = function() {
		//$scope.raidbuffs.stamina = [166928, "stamina_warlock_opt"];
		//$scope.raidbuffs.sp = [109773, "sp_warlock"];
		//$scope.raidbuffs.multistrike = [109773, "multistrike_warlock"];
	};

	$scope.computeRaidBuffs();
	
	$scope.charDimmedInRoster = function(char) {
		var comp = $scope.slots[$scope.tab_selected];
		var in_comp = false;
		if (comp) {
			for(var slot in comp) {
				if (comp[slot].owner === char.owner) {
					in_comp = true;
					break;
				}
			}
		}
		
		return (char && $scope.picked && char.owner == $scope.picked.owner) || in_comp;
	};
	
	$scope.charVisibleInComp = function(char) {
		return (char && (!$scope.picked || char.owner != $scope.picked.owner)) ? 1 : 0;
	};
});

GuildTools.controller("CalendarAddTabCtrl", function($scope) {
	$scope.inflight = false;
	
	$scope.create = function() {
		$scope.inflight = true;
		$.call("calendar:tab:create", { title: $scope.title }, function(err) {
			$scope.inflight = false;
			if (err) {
				$scope.title = "";
				_("#add-tab-title").focus();
			} else {
				$scope.modal();
			}
		});
	};
});

GuildTools.controller("CalendarRenameTabCtrl", function($scope) {
	$scope.inflight = false;
	var tab = $scope.modalCtx;
	
	$scope.title = tab.title;
	
	$scope.rename = function() {
		$scope.inflight = true;
		$.call("calendar:tab:rename", { id: tab.id, title: $scope.title }, function() {
			$scope.modal();
		});
	};
});
