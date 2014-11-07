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
	$scope.absences = [];

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
			$: function(data) {
				$scope.events = {};
				$scope.answers = {};
				data.events.forEach(function(entry) {
					pushEvent(entry.event);
					$scope.answers[entry.id] = entry.answer;
				});
				$scope.absences = data.absences;
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
			},

			"absence:create": function(abs) {
				$scope.absences.push(abs);
			},

			"absence:update": function(abs) {
				$scope.absences = $scope.absences.map(function(old) {
					return old.id === abs.id ? abs : old;
				});
			},

			"absence:delete": function (id) {
				$scope.absences = $scope.absences.filter(function(abs) {
					return abs.id !== id;
				});
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

		var today_day = (today.getDay() + 6) % 7;
		var day_in_lockout = (today_day + 5) % 7;

		var lockout_start = new Date(today.getFullYear(), today.getMonth(), today.getDate() - day_in_lockout);
		var lockout_end   = new Date(today.getFullYear(), today.getMonth(), today.getDate() + (6 - day_in_lockout));

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

				var day_date = new Date(year, month, day);
				var cell = $scope.data[r][c];
				cell.day = day;
				cell.month = month + 1;
				cell.year = year;
				cell.inactive = !(day_date >= lockout_start && day_date <= lockout_end);
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
				return "Restricted event";
			case 4:
				return "Announce";
			case 5:
				return "Optional event";
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

	var absents_cache = {};

	$scope.hasAbsentsOn = function(day) {
		var date = new Date(day);
		date.setHours(0, 0, 0, 0);

		var absent_ids = {};

		var cache_entry = $scope.absences.filter(function(abs) {
			if ((new Date(abs.from) <= date && new Date(abs.to) >= date) && !absent_ids[abs.user]) {
				return (absent_ids[abs.user] = true);
			} else {
				return false;
			}
		});

		cache_entry = cache_entry.map(function(abs) {
			return $.roster.mainForUser(abs.user);
		});

		absents_cache[day] = cache_entry;
		return cache_entry.length > 0;
	};

	$scope.absentsForDay = function(day) {
		return absents_cache[day];
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
	$scope.answers_tab = { "0": [], "1": [], "2": []};
	$scope.chars = [];
	$scope.tabs = [];
	$scope.tabs_idx = {};
	$scope.slots = {};
	$scope.tab_selected = 0;
	$scope.lock = null;
	$scope.editable = false;
	$scope.absences = [];

	$scope.setTab = function(t) {
		$scope.tab_selected = t;
		$scope.computeRaidBuffs();
	};

	$scope.$watch("tab_selected", function(tab) {
		$scope.updateNote();
		if (tab === 0 || !$scope.editable) return;
		$.exec("calendar:lock:status", { id: tab }, function(err, data) {
			$scope.lock = err ? null : data.owner;
		});
	});

	var cached_tab;
	var cached_groups;
	$scope.roster_tab = 1;

	$scope.answer = 1;
	$scope.answer_note = "";

	$scope.picked = null;
	$scope.pickedFromSlot = false;
	$scope.setPicker = function(char, ev, slot) {
		if (!char || !$scope.editable || ev.button !== 0) return false;
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
			tab: $scope.tab_selected
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
			if ($scope.picked.unknown) {
				template.char.role = "DPS";
			}
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

	_("#calendar-event").mousemove($scope.handlePicker);

	function extract_main(data) {
		return function(char) {
			if ((!data.main && char.main) || char.id === data.answer.char) {
				data.main = char;
				return true;
			}
		};
	}

	function build_tabs_idx() {
		$scope.tabs_idx = {};
		$scope.tabs.forEach(function(tab) {
			$scope.tabs_idx[tab.id] = tab;
		});
	}

	function update_answer(answer) {
		if (answer.user === $.user.id) {
			$scope.answer = answer.answer;
			$scope.answer_note = answer.note;
		}

		$scope.answers[answer.user] = answer;
		build_answers_tabs();
	}

	function find_user_absence(id) {
		return $scope.absences.filter(function(abs) { return abs.user == id; })[0];
	}

	function build_answers_tabs() {
		$scope.answers_tab = { "0": [], "1": [], "2": []};
		for (var user in $scope.answers) {
			var absence = find_user_absence(user);
			var answer = $scope.answers[user] || { answer: absence ? 2 : 0 };
			$scope.answers_tab[answer.answer].push({ user: user, answer: answer, absence: absence });
		}

		cached_tab = null;
		$scope.updateNote();
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

			$scope.absences = data.absences;
			$scope.answers = data.answers;
			build_answers_tabs();

			build_tabs_idx();
			cached_tab = null;
			$scope.computeRaidBuffs();
			$scope.updateNote();
			$scope.chars = $.roster.charsByUser($.user.id);
		},

		"event:update": function(data) {
			$scope.event = data;
		},

		"event:update:full": function(data) {
			$scope.event = data.event;
			$scope.tabs = data.tabs;
			build_tabs_idx();
			$scope.slots = data.slots;
			$scope.updateNote();
		},

		"event:delete": function(data) {
			$scope.breadcrumb.push("/calendar");
			$scope.error("Event deleted");
		},

		"answer:create": update_answer,
		"answer:update": update_answer,

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

			if (data.tab === $scope.tab_selected) {
				$scope.computeRaidBuffs();
				$scope.updateNote();
			}
		},

		"calendar:slot:delete": function(data) {
			var comp = $scope.slots[data.tab];
			if (comp) delete comp[data.slot];
			if (data.tab === $scope.tab_selected) {
				$scope.computeRaidBuffs();
				$scope.updateNote();
			}
		},

		"calendar:tab:create": function(tab) {
			$scope.tabs.push(tab);
			build_tabs_idx();
		},

		"calendar:tab:update": function(tab_new) {
			$scope.tabs = $scope.tabs.map(function(tab) {
				return (tab.id === tab_new.id) ? tab_new : tab;
			});
			if (tab_new.locked && !$scope.editable) { delete $scope.slots[tab_new.id]; }
			build_tabs_idx();
			$scope.updateNote();
		},

		"calendar:tab:delete": function(id) {
			$scope.tabs = $scope.tabs.filter(function(tab) {
				return tab.id !== id;
			});

			if ($scope.tab_selected === id) {
				$scope.tab_selected = $scope.tabs[0].id;
			}
		},

		"calendar:tab:wipe": function(id) {
			delete $scope.slots[id];
			if (id === $scope.tab_selected) {
				$scope.computeRaidBuffs();
				$scope.updateNote();
			}
		},

		"calendar:lock:acquire": function(data) {
			if (data.id === $scope.tab_selected) {
				$scope.lock = data.owner;
			}
		},

		"calendar:lock:release": function(id) {
			if (id === $scope.tab_selected) {
				$scope.lock = null;
			}
		},

		"absence:create": function(abs) {
			$scope.absences.push(abs);
			build_answers_tabs();
		},

		"absence:update": function(abs) {
			$scope.absences = $scope.absences.map(function(old) {
				return old.id === abs.id ? abs : old;
			});
			build_answers_tabs();
		},

		"absence:delete": function (id) {
			$scope.absences = $scope.absences.filter(function(abs) {
				return abs.id !== id;
			});
			build_answers_tabs();
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

	$scope.$on("roster-updated", function() {
		cached_tab = null;
		$scope.chars = $.roster.charsByUser($.user.id);
	});

	$scope.getAnswersGroups = function(tab) {
		if (cached_tab === tab) {
			return cached_groups;
		}

		var groups = [];
		var group = [];

		if (!$scope.answers_tab[tab]) return [];

		function select_char(e) {
			var char;
			if (e.answer && e.answer.char) {
				return $.roster.char(e.answer.char);
			} else {
				return $.roster.mainForUser(e.user);
			}
		}

		var list = $scope.answers_tab[tab].map(function (e) {
			return {
				user: e.user,
				answer: e.answer,
				absence: e.absence,
				char: select_char(e)
			};
		});

		list.sort(function (a, b) {
			a = a.char;
			b = b.char;
			if (a["class"] !== b["class"]) return a["class"] - b["class"];
			return a.name.localeCompare(b.name);
		});

		list.forEach(function (answer) {
			group.push(answer);
			if (group.length >= 2) {
				groups.push(group);
				group = [];
			}
		});

		if (group.length) {
			if (group.length < 2) {
				group.push({void: true});
			}

			groups.push(group);
		}

		cached_tab = tab;
		cached_groups = groups;

		return groups;
	};

	$scope.getPlayerIcon = function(row) {
		if (row.answer.note) {
			return "pencil-squared";
		}

		if (row.absence) {
			return "flash";
		}

		return "void";
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
					alert("NYI");
				},
				order: 11
			},
			{
				icon: "awe-pencil",
				text: "Edit event",
				action: function() {
					$scope.modal("calendar-edit-desc", $scope.event);
				},
				order: 12
			},
			{
				icon: "awe-trash",
				text: "Delete",
				action: function() {
					if (confirm("Are you sure?"))
						$.call("calendar:delete", { id: $scope.event.id });
				},
				order: 13
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
				order: 1
			},
			{
				icon: "awe-lock",
				text: "Lock tab",
				action: function() {
					$.call("calendar:tab:lock", { id: tab.id });
				},
				order: 2, visible: !tab.locked
			},
			{
				icon: "awe-lock-open-alt",
				text: "Unlock tab",
				action: function() {
					$.call("calendar:tab:unlock", { id: tab.id });
				},
				order: 3, visible: tab.locked
			},
			{ separator: true, order: 4, visible: $scope.tabs.length > 1 },
			{
				icon: "awe-left-dir",
				text: "Move left",
				action: function() {
					var target = extract($scope.tabs.filter(lesserThan(tab)).reduce(max, -1));
					$.call("calendar:tab:swap", { a: target.id, b: tab.id });
				},
				order: 5,
				visible: $scope.tabs.filter(lesserThan(tab)).length > 0
			},
			{
				icon: "awe-right-dir",
				text: "Move right",
				action: function() {
					var target = extract($scope.tabs.filter(greaterThan(tab)).reduce(min, -1));
					$.call("calendar:tab:swap", { a: target.id, b: tab.id });
				},
				order: 6,
				visible: $scope.tabs.filter(greaterThan(tab)).length > 0
			},
			{ separator: true, order: 10, visible: !tab.undeletable },
			{
				icon: "awe-trash",
				text: "Delete",
				action: function() {
					if (confirm("Are you sure?"))
						$.call("calendar:tab:delete", { id: tab.id });
				},
				order: 11, visible: !tab.undeletable
			}
		];

		$scope.menu(menu, ev);
	};

	$scope.slotMenu = function(slot, ev) {
		var template = { tab: $scope.tab_selected, slot: slot, char: $scope.slots[$scope.tab_selected][slot] };
		var chars;

		try {
			chars = $.roster.charsByUser($scope.slots[$scope.tab_selected][slot].owner).filter(function(char) {
				return char.name !== template.char.name && char.active;
			});
		} catch (e) {
			chars = [];
		}

		var menu = [
			{
				icon: "icn-tank",
				text: "Tank",
				action: function() {
					template.char.role = "TANK";
					$.call("calendar:comp:set", template);
				},
				order: 0
			},
			{
				icon: "icn-heal",
				text: "Heal",
				action: function() {
					template.char.role = "HEALING";
					$.call("calendar:comp:set", template);
				},
				order: 1
			},
			{
				icon: "icn-dps",
				text: "DPS",
				action: function() {
					template.char.role = "DPS";
					$.call("calendar:comp:set", template);
				},
				order: 2
			},
			{ separator: true, order: 3, visible: chars.length > 0 }
		];

		chars.sort(function(a, b) { return b.ilvl - a.ilvl; });
		chars.forEach(function(char, i) {
			menu.push({
				icon: "cls-icon c" + char["class"],
				text: char.name, text2: " (" + char.ilvl + ")",
				action: function() {
					template.char = char;
					$.call("calendar:comp:set", template);
				},
				order: i + 10
			});
		});

		$scope.menu(menu, ev);
	};

	$scope.raidbuffs = {};

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
		{ b: "haste", c: 7, r: "DPS", w: 116956, i: "haste_shaman_dps" },
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
		var buffs = $scope.raidbuffs = {
			stats: [114708, false],
			stamina: [114713, false],
			ap: [114716, false],
			sp: [114717, false],
			crit: [114721, false],
			haste: [114719, false],
			mastery: [114722, false],
			multistrike: [167411, false],
			versatility: [167409, false]
		};

		var have = {};

		var slots = $scope.slots[$scope.tab_selected];
		for (var slot in slots) {
			var char = slots[slot];
			have[char["class"]] = have[char["class"]] ? have[char["class"]] + 1 : 1;
			have[char["class"] + ":" + char.role] = have[char["class"] + ":" + char.role] ? have[char["class"] + ":" + char.role] + 1 : 1;
		}

		buffs_table.forEach(function(buff) {
			if (buffs[buff.b][1] || buff.opt) return;
			if (!have[buff.c]) return;
			if (buff.r && !have[buff.c + ":" + buff.r]) return;
			buffs[buff.b] = [buff.w, buff.i];
		});

		buffs_table.forEach(function(buff) {
			if (buffs[buff.b][1] || !buff.opt) return;
			if (!have[buff.c] || have[buff.c] < 1) return;
			if (buff.r && (!have[buff.c + ":" + buff.r] || have[buff.c + ":" + buff.r] < 1)) return;
			--have[buff.c];
			--have[buff.c + ":" + buff.r];
			buffs[buff.b] = [buff.w, buff.i];
		});
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

	$scope.playerIsAvailable = function(owner) {
		return $scope.answers[owner] && $scope.answers[owner].answer == 1;
	};

	var tips = [
		"Nearby questgivers that are awaiting your return are shown as a question mark on your mini-map.",
		"Your spell casting can be cancelled by moving, jumping or hitting the escape key.",
		"Clicking on a player name in the chat window lets you send a private message to them.",
		"If you <Shift>Click on a player name in the chat window it tells you additional information about them.",
		"You can <Control>Click on an item to see how you would look wearing that item.",
		"An item with its name in gray is a poor quality item and generally can be sold to a vendor.",
		"An item with its name in white is useful to players in some way and can be used or sold at the auction house.",
		"If you are lost trying to complete a quest, the quest log will often tell you what to do next.",
		"You can send mail to other players or even to your other characters from any mailbox in game.",
		"You can <Shift>Click on an item to place an item link into a chat message.",
		"You can remove a friendly spell enhancement on yourself by right-clicking on the spell effect icon.",
		"When you learn a profession or secondary skill the button that allows you to perform that skill is found in the general tab of your spellbook.",
		"All of your action bars can have their hotkeys remapped in the key bindings interface.",
		"If a profession trainer cannot teach you any more, they will generally tell you where to go to get further training.",
		"On your character sheet is a reputation tab that tells you your status with different groups.",
		"You can use the Tab key to select nearby enemies in front of you.",
		"If you are having trouble finding something in a capital city, try asking a guard for directions.",
		"You can perform many fun actions with the emote system, for instance you can type /dance to dance.",
		"A Blizzard employee will NEVER ask for your password.",
		"You can only know two professions at a time, but you can learn all of the secondary skills (archaeology, fishing, cooking and first-aid).",
		"You can right-click on a beneficial spell that has been cast on you to dismiss it.",
		"The interface options menu <ESC> has lots of ways to customize your game play.",
		"You can turn off the slow scrolling of quest text in the interface options menu.",
		"Spend your talent points carefully as once your talents are chosen, you must spend gold to unlearn them.",
		"A mail icon next to the minimap means you have new mail. Visit a mailbox to retrieve it.",
		"You can add additional action bars to your game interface from the interface options menu.",
		"If you hold down <Shift> while right-clicking on a target to loot, you will automatically loot all items on the target.",
		"Your character can eat and drink at the same time.",
		"If you enjoyed playing with someone, put them on your friends list!",
		"Use the Looking for Group interface (\"I\" Hotkey) to find a group or add more players to your group.",
		"There are a number of different loot options when in a group. The group leader can right-click their own portrait to change the options.",
		"You can choose not to display your helm and/or cloak with an option from the interface options menu.",
		"You can target members of your party with the function keys. F1 targets you; F2 targets the second party member.",
		"Being polite while in a group with others will get you invited back!",
		"Remember to take all things in moderation (even World of Warcraft!)",
		"You can click on a faction in the reputation pane to get additional information and options about that faction.",
		"A monster with a silver dragon around its portrait is a rare monster with better than average treasure.",
		"If you mouse over a chat pane it will become visible and you can right-click on the chat pane tab for options.",
		"Sharing an account with someone else can compromise its security.",
		"You can display the duration of beneficial spells on you from the interface options menu.",
		"You can lock your action bar so you don't accidentally move spells. This is done using the interface options menu.",
		"You can assign a Hotkey to toggle locking/unlocking your action bar. Just look in the Key Bindings options to set it.",
		"You can cast a spell on yourself without deselecting your current target by holding down <Alt> while pressing your hotkey.",
		"Ensure that all party members are on the same stage of an escort quest before beginning it.",
		"You're much less likely to encounter wandering monsters while following a road.",
		"Killing guards gives no honor.",
		"You can hide your interface with <Alt>Z and take screenshots with <Print Screen>.",
		"Typing /macro will bring up the interface to create macros.",
		"Enemy players whose names appear in gray are much lower level than you are and will not give honor when killed.",
		"From the Raid UI you can drag a player to the game field to see their status or drag a class icon to see all members of that class.",
		"A blue question mark above a quest giver means the quest is repeatable.",
		"Use the assist button (F key) while targeting another player, and it will target the same target as that player.",
		"<Shift>Clicking on an item being sold by a vendor will let you select how many of that item you wish to purchase.",
		"Playing in a battleground on its holiday weekend increases your honor gained.",
		"If you are having trouble fishing in an area, try attaching a lure to your fishing pole.",
		"You can view messages you previously sent in chat by pressing <Alt> and the up arrow key.",
		"You can Shift-Click on an item stack to split it into smaller stacks.",
		"Pressing both mouse buttons simultaneously will make your character run.",
		"When replying to a tell from a player (Default 'R'), the <TAB> key cycles through people you have recently replied to.",
		"Clicking an item name that appears bracketed in chat will tell you more about the item.",
		"It's considered polite to talk to someone before inviting them into a group, or opening a trade window.",
		"Pressing 'v' will toggle the display of a health bar over nearby enemies.",
		"Your items do not suffer durability damage when you are killed by an enemy player.",
		"<Shift>click on a quest in your quest log to toggle quest tracking for that quest.",
		"There is no cow level.",
		"The auction houses in each of your faction's major cities are linked together.",
		"Nearby questgivers that are awaiting your return are shown as a yellow question mark on your mini-map.",
		"Quests completed at maximum level award money instead of experience.",
		"<Shift>-B will open all your bags at once.",
		"When interacting with other players a little kindness goes a long way!",
		"Bring your friends to Azeroth, but don't forget to go outside Azeroth with them as well.",
		"If you keep an empty mailbox, the mail icon will let you know when you have new mail waiting!",
		"Never give another player your account information.",
		"When a player not in your group damages a monster before you do, it will display a gray health bar and you will get no loot or experience from killing it.",
		"You can see the spell that your current target is casting by turning on the 'Show Enemy Cast Bar' options in the basic interface options.",
		"You can see the target of your current target by turning on the 'Show Target of Target' option in the advanced interface options tab.",
		"You can access the map either by clicking the map button in the upper left of the mini-map or by hitting the 'M' key.",
		"Many high level dungeons have a heroic mode setting. Heroic mode dungeons are tuned for level 70 players and have improved loot.",
		"Spend your honor points for powerful rewards at the Champion's Hall (Alliance) or Hall of Legends (Horde).",
		"The honor points you earn each day become available immediately. Check the PvP interface to see how many points you have to spend.",
		"You can turn these tips off in the Interface menu.",
		"Dungeon meeting stones can be used to summon absent party members. It requires two players at the stone to do a summoning.",
		"The Parental Controls section of the Account Management site offers tools to help you manage your play time.",
		"Quest items that are in the bank cannot be used to complete quests.",
		"A quest marked as (Failed) in the quest log can be abandoned and then reacquired from the quest giver.",
		"The number next to the quest name in your log is how many other party members are on that quest.",
		"You cannot advance quests other than (Raid) quests while you are in a raid group.",
		"You cannot cancel your bids in the auction house so bid carefully.",
		"To enter a chat channel, type /join [channel name] and /leave [channel name] to exit.",
		"Mail will be kept for a maximum of 30 days before it disappears.",
		"Once you get a key, they can be found in a special key ring bag that is to the left of your bags.",
		"You can replace a gem that is already socketed into your item by dropping a new gem on top of it in the socketing interface.",
		"City Guards will often give you directions to other locations of note in the city.",
		"You can repurchase items you have recently sold to a vendor from the buyback tab.",
		"A group leader can reset their instances from their portrait right-click menu.",
		"You can always get a new hearthstone from any Innkeeper.",
		"You can open a small map of the current zone either with Shift-M or as an option from the world map.",
		"Players cannot dodge, parry, or block attacks that come from behind them.",
		"If you Right Click on a name in the combat log a list of options will appear.",
		"You can only have one Battle Elixir and one Guardian Elixir on you at a time.",
		"The calendar can tell you when raids reset.",
		"Creatures cannot make critical hits with spells, but players can.",
		"Creatures can dodge attacks from behind, but players cannot. Neither creatures nor players can parry attacks from behind.",
		"Players with the Inscription profession can make glyphs to improve your favorite spells and abilities.",
		"Don't stand in the fire!",
		"The Raid UI can be customized in a number of different ways, such as how it shows debuffs or current health.",
		"Dungeons are more fun when everyone works together as a team. Be patient with players who are still learning the game."
	];

	$scope.randomTip = function() {
		if (!$scope.tabs_idx[$scope.tab_selected])
			return "Note unavailable";

		var i, l;

		var base = Math.sin($scope.event.id) * 100000;
		var e_title = $scope.event.title;
		for (i = 0, l = e_title.length; i < l; ++i)
			base += Math.sin(e_title.charCodeAt(i) * base) * 100000;

		base += Math.sin($scope.tab_selected) * 100000;
		var title = $scope.tabs_idx[$scope.tab_selected].title;
		for (i = 0, l = title.length; i < l; ++i)
			base += Math.sin(title.charCodeAt(i) * base) * 100000;

		return tips[Math.floor(Math.abs(base)) % tips.length];
	};

	$scope.editNote = function() {
		$.call("calendar:lock:acquire", { id: $scope.tab_selected }, function(err) {
			if (!err) {
				$scope.modal("calendar-edit-note", { id: $scope.tab_selected, note: $scope.tabs_idx[$scope.tab_selected].note }, true);
			}
		});
	};

	$scope.cached_note = "";

	$scope.updateNote = function() {
		$scope.cached_note = $scope.$eval("(preprocessNote(tabs_idx[tab_selected].note) || '**Nothing has been written yet, but did you know that...**\n\n*' + randomTip() + '*') | markdown");
	};

	$scope.updateNote();

	$scope.preprocessNote = function(note) {
		if (!note) return note;

		var roster = [];

		function process_char(char) {
			roster.push({
				name: removeDiacritics(char.name),
				char: char
			});
		}

		for(var id in $scope.answers) {
			$.roster.charsByUser(id).forEach(process_char);
		}

		note = note.replace(/@(\w+)/g, function(match, name) {
			var simple_name = removeDiacritics(name);
			var sample_length = simple_name.length;
			var candidate = null;

			var matches = roster.map(function(candidate) {
				return {
					scoreA: levenshtein(candidate.name.slice(0, sample_length), simple_name),
					scoreB: levenshtein(candidate.char.name.slice(0, sample_length), name),
					char: candidate.char
				};
			}).filter(function(candidate) {
				return candidate.scoreA < (sample_length / 2);
			});

			matches.sort(function(a, b) {
				if (a.scoreA !== b.scoreA) return a.scoreA - b.scoreA;
				return a.scoreB - b.scoreB;
			});

			candidate = matches[0] && matches[0].char;

			var found = false;
			if (candidate) {
				for (var slot in $scope.slots[$scope.tab_selected]) {
					var slot_char = $scope.slots[$scope.tab_selected][slot];
					if (!found && slot_char.owner == candidate.owner) {
						candidate = slot_char;
						found = true;
						break;
					}
				}
			}

			return (candidate) ? "@" + candidate.name + "(" + candidate["class"] + ")" + (found ? "" : ":") + "@" : "@" + name + "(99)" + "@";
		});

		return note;
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

GuildTools.controller("CalendarEditDescCtrl", function($scope) {
	$scope.inflight = false;
	var event = $scope.modalCtx;

	$scope.desc = event.desc;
	$scope.title = event.title;

	$scope.save = function() {
		$scope.inflight = true;
		$.call("calendar:event:editdesc", { title: $scope.title, desc: $scope.desc.replace(/^\s+|\s+$/, "") }, function() {
			$scope.modal();
		});
	};
});

GuildTools.controller("CalendarEditNoteCtrl", function($scope) {
	$scope.inflight = false;

	var tab_id = $scope.modalCtx.id;
	$scope.note = $scope.modalCtx.note || "";

	var lockPing = setInterval(function() {
		$.exec("calendar:lock:refresh");
	}, 10000);

	$scope.$on("$destroy", function() {
		clearInterval(lockPing);
		$.exec("calendar:lock:release");
	});

	$scope.save = function() {
		$scope.inflight = true;
		$.call("calendar:tab:edit", { id: tab_id, note: $scope.note.replace(/^\s+|\s+$/, "") || null }, function() {
			$scope.modal();
		});
	};
});
