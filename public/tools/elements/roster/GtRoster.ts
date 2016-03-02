import {Element, Inject, Property} from "../../polymer/Annotations";
import {TabsGenerator, View} from "../../client/router/View";
import {GtBox, GtAlert} from "../widgets/GtBox";
import {GtButton} from "../widgets/GtButton";
import {GtCheckbox} from "../widgets/GtCheckbox";
import {GtLabel} from "../widgets/GtLabel";
import {RosterFilters} from "./RosterFilters";
import {RosterItem} from "./RosterItem";
import {PolymerElement} from "../../polymer/PolymerElement";
import {RosterService, QueryResult} from "../../services/roster/RosterService";
import {On} from "../../polymer/Annotations";

const RosterTabs: TabsGenerator = (view, path, user) => [
	{ title: "Roster", link: "/roster", active: view == "gt-roster" }
];

@View("roster", RosterTabs)
@Element({
	selector: "gt-roster",
	template: "/assets/views/roster.html",
	dependencies: [GtBox, GtButton, GtCheckbox, GtLabel, RosterFilters, RosterItem, GtAlert]
})
export class GtRoster extends PolymerElement {
	@Inject
	@On({ "preload-done": "OnPreloadDone" })
	private roster: RosterService;

	/**
	 * Current display mode
	 */
	@Property({ observer: "UpdateFilters" })
	private players_view = true;

	private ViewPlayers() {
		this.players_view = true;
	}

	private ViewChars() {
		this.players_view = false;
	}

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

	/**
	 * When the roster service finish preloading users, force refresh of
	 * the roster list because we have obviously no results shown.
	 */
	private OnPreloadDone() {
		this.UpdateSearch(true);
	}
}
