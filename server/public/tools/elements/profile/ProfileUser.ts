import {Element, Property} from "../../polymer/Annotations";
import {GtBox} from "../widgets/GtBox";
import {BnetThumb} from "../misc/BnetThumb";
import {PolymerElement} from "../../polymer/PolymerElement";

@Element({
	selector: "profile-user",
	template: "/assets/views/profile.html",
	dependencies: [GtBox, BnetThumb]
})
export class ProfileUser extends PolymerElement {
	@Property public user: number;
}
