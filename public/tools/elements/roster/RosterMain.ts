import {Element, Property} from "../../polymer/Annotations";
import {PolymerElement} from "../../polymer/PolymerElement";

///////////////////////////////////////////////////////////////////////////////
// <gt-roster-main>

@Element({
	selector: "roster-main",
	template: "/assets/views/roster.html"
})
export class RosterMain extends PolymerElement {
	@Property
	public user: number;
}
