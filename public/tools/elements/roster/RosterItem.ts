import {Element, Property, Listener} from "../../polymer/Annotations";
import {GtTimeago} from "../misc/GtTimeago";
import {BnetThumb} from "../misc/BnetThumb";
import {GtBox} from "../widgets/GtBox";
import {PolymerElement} from "../../polymer/PolymerElement";
import {QueryResult, Char} from "../../services/roster/RosterService";

///////////////////////////////////////////////////////////////////////////////
// <gt-roster-item>

@Element({
	selector: "roster-item",
	template: "/assets/views/roster.html",
	dependencies: [GtBox, BnetThumb, GtTimeago]
})
export class RosterItem extends PolymerElement {
	@Property
	public data: QueryResult;

	@Property
	public player: boolean;

	@Property({computed: "data.chars player"})
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
