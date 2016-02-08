import { Element, Dependencies, Listener, PolymerElement, Inject, Property, PolymerModelEvent } from "elements/polymer";
import { View, TabsGenerator } from "elements/app";
import { GtBox, GtAlert } from "elements/box";
import { GtInput, GtButton, GtCheckbox, GtLabel } from "elements/widgets";
import { BnetThumb } from "elements/bnet";
import { GtDialog } from "elements/dialog";
import { GtTimeago } from "elements/timeago";
import { Server } from "client/server";
import { User, Char, QueryResult, RosterService } from "services/roster";

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
	@Property public open: boolean = true;

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
	public filters: string = location.hash.slice(1).replace(/_/g, " ");

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
// <gt-roster-item>

@Element("gt-roster-item", "/assets/views/roster.html")
@Dependencies(GtBox, BnetThumb, GtTimeago)
export class GtRosterItem extends PolymerElement {
	@Property
	public data: QueryResult;

	@Property
	public player: boolean;

	@Property({ computed: "data.chars player" })
	public get alts_list(): Char[] {
		if (!this.player) return [];
		return this.data.chars.slice(0, 5);
	}

	/**
	 * Click on the element navigate to the user profile
	 */
	@Listener("click")
	private OnClick() {
		let data = <any> this.data;
		let id = (this.player) ? data.user.id : data.owner;
		if (id == this.app.user.id) this.app.router.goto("/profile");
		else this.app.router.goto(`/profile/${id}`);
	}
}

///////////////////////////////////////////////////////////////////////////////
// <gt-roster>

@View("roster", RosterTabs)
@Element("gt-roster", "/assets/views/roster.html")
@Dependencies(GtBox, GtButton, GtCheckbox, GtLabel, GtRosterFilters, GtRosterItem, GtAlert)
export class GtRoster extends PolymerElement {
	@Inject
	private roster: RosterService;

	/**
	 * Current display mode
	 */
	@Property({ observer: "UpdateFilters" })
	private players_view = true;

	private ViewPlayers() { this.players_view = true; }
	private ViewChars() { this.players_view = false; }

	/**
	 * Roster entries
	 */
	private results: QueryResult[] = [];
	private matching: string = "...";

	/**
	 * The raw search string
	 */
	public raw_search: string = location.hash;

	/**
	 * Indicates if a filter is currently defined
	 */
	@Property({ computed: "raw_search" })
	private get has_search(): boolean {
		return this.raw_search.trim().length > 0;
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
		this.UpdateSearch(true);
	}

	/**
	 * The search field was modified, refresh the roster view
	 */
	private UpdateSearch(instant: boolean = false) {
		let query = this.$.search.value;
		this.raw_search = query;
		this.debounce("UpdateSearch", () => {
			location.hash = query.replace(/ /g, "_");
			if (this.players_view) {
				this.results = this.roster.executeQuery(query);

				let players = this.results.length;
				let ps = players != 1 ? "s" : "";

				let chars = 0;
				this.results.forEach(p => chars += p.chars.length);
				let cs = chars != 1 ? "s" : "";

				this.matching = `${players} player${ps} (${chars} char${cs})`;
			} else {
				this.results = <any> this.roster.executeQueryChars(query);
				let s = this.results.length != 1 ? "s" : "";
				this.matching = `${this.results.length} char${s}`;
			}
		}, instant ? void 0 : 250);
	}
}
