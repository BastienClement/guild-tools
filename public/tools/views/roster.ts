import { Element, Dependencies, PolymerElement, Inject, Property, PolymerModelEvent } from "elements/polymer";
import { View, TabsGenerator } from "elements/app";
import { GtBox } from "elements/box";
import { GtInput, GtButton, GtCheckbox, GtLabel } from "elements/widgets";
import { GtDialog } from "elements/dialog";
import { Server } from "client/server";
import { User, Roster } from "services/roster";

const RosterTabs: TabsGenerator = (view, path, user) => [
	{ title: "Roster", link: "/roster", active: view == "views/roster/GtRoster" }
];

///////////////////////////////////////////////////////////////////////////////
// <gt-roster-filters-dropdown>

@Element("gt-roster-filters-dropdown", "/assets/views/roster.html")
export class GtRosterFiltersDropdown extends PolymerElement {
	/**
	 * The category icon
	 */
	@Property public icon: string;

	/**
	 * The category title
	 */
	@Property public title: string;

	/**
	 * Current dropdown state
	 */
	@Property public open: boolean = false;

	/**
	 * Toggle the dropdown state
	 */
	private Toggle() {
		this.open = !this.open;
	}
}

///////////////////////////////////////////////////////////////////////////////
// <gt-roster-filters>

@Element("gt-roster-filters", "/assets/views/roster.html")
@Dependencies(GtBox, GtCheckbox, GtLabel, GtRosterFiltersDropdown)
export class GtRosterFilters extends PolymerElement {
	/**
	 * The list of activated filters as text
	 */
	@Property({ notify: true })
	public filters: string = "";

	/**
	 * Updates the filters string based on user selection
	 */
	private Update() {
		// Remove type-checking for filter flags
		let _: any = this;
		let res: string[] = [];

		// Common algorithm for building filters string
		const apply_map = (key: string, mapping: { [key: string]: string; }) => {
			let opts: string[] = [];
			for (let key in mapping)
				if (_[key]) opts.push(mapping[key]);
			if (opts.length > 0) res.push(key + ":" + opts.join(","));
		};

		apply_map("rank", {
			rank_officer: "officer",
			rank_member: "member",
			rank_apply: "apply",
			rank_casual: "casual",
			rank_veteran: "veteran",
			rank_guest: "guest"
		});

		apply_map("class", {
			class_warrior: "warrior",
			class_paladin: "paladin",
			class_hunter: "hunter",
			class_rogue: "rogue",
			class_priest: "priest",
			class_dk: "deathknight",
			class_shaman: "shaman",
			class_mage: "mage",
			class_warlock: "warlock",
			class_monk: "monk",
			class_druid: "druid",
			class_dh: "demonhunter"
		});

		apply_map("race", {
			race_orc: "orc",
			race_human: "human",
			race_undead: "undead",
			race_dwarf: "dwarf",
			race_tauren: "tauren",
			race_ne: "nightelf",
			race_troll: "troll",
			race_gnome: "gnome",
			race_be: "bloodelf",
			race_draenei: "draenei",
			race_goblin: "goblin",
			race_worgen: "worgen",
			race_pandaren: "pandaren"
		});

		apply_map("level", {
			level_1_99: "1-99",
			level_100: "100",
			level_101_109: "101-109",
			level_110: "110"
		});

		apply_map("ilvl", {
			ilvl_0_699: "-699",
			ilvl_700_714: "700-714",
			ilvl_715_729: "715-729",
			ilvl_730_plus: "730-"
		});

		apply_map("role", {
			role_tank: "tank",
			role_healer: "healer",
			role_dps: "dps"
		});

		apply_map("armor", {
			armor_cloth: "cloth",
			armor_leather: "leather",
			armor_mail: "mail",
			armor_plate: "plate"
		});

		// Special handling for flags
		let flags: string[] = [];
		if (_.flag_main && !_.flag_alt) flags.push("main:1");
		if (!_.flag_main && _.flag_alt) flags.push("main:0");
		if (_.flag_active && !_.flag_inactive) flags.push("active:1");
		if (!_.flag_active && _.flag_inactive) flags.push("active:0");
		if (_.flag_valid && !_.flag_invalid) flags.push("valid:1");
		if (!_.flag_valid && _.flag_invalid) flags.push("valid:0");
		if (_.flag_guilded && !_.flag_unguilded) flags.push("guilded:1");
		if (!_.flag_guilded && _.flag_unguilded) flags.push("guilded:0");
		flags.forEach(f => res.push(f));

		// Commit result
		this.filters = res.join(" ");
	}
}

///////////////////////////////////////////////////////////////////////////////
// <gt-roster>

@View("roster", RosterTabs)
@Element("gt-roster", "/assets/views/roster.html")
@Dependencies(GtBox, GtButton, GtCheckbox, GtLabel, GtRosterFilters)
export class GtRoster extends PolymerElement {
	@Inject
	private roster: Roster;

	/**
	 * Current display mode
	 */
	private view_mode = "grid";

	/**
	 * Enables grid view mode
	 */
	private ViewGrid() { this.view_mode = "grid"; }

	/**
	 * Enables list view mode
	 */
	private ViewList() { this.view_mode = "list"; }

	/**
	 * Should the filters panel be displayed
	 */
	private show_filters: boolean = localStorage.getItem("roster.filters.visible") === "1";

	/**
	 * Toggles the visibility of the filters panel
	 */
	private ToggleFilters() {
		this.show_filters = !this.show_filters;
		localStorage.setItem("roster.filters.visible", this.show_filters ? "1" : "0");
	}

	/**
	 * List of filters from the filters selector panel
	 */
	@Property({ observer: "UpdateFilters" })
	public filters: string = "";

	/**
	 * The filters selection was changed, update search input field
	 */
	private UpdateFilters() {
		let search_field: HTMLInputElement = this.$.search;
		// Remove old filters and useless spaces
		let value = search_field.value.replace(/[a-z]+:[^ ]+/g, "").replace(/\s{2,}/g, " ").trim();
		if (this.filters.length > 0) {
			value = value.length > 0 ? `${value} ${this.filters}` : this.filters;
		}
		search_field.value = value;
		this.UpdateSearch();
	}

	/**
	 * The search field was modified, refresh the roster view
	 */
	private UpdateSearch() {
		this.debounce("UpdateSearch", () => {
			let query = this.$.search.value;
			console.log(this.roster.executeQuery(query));
		}, 250);
	}
}
