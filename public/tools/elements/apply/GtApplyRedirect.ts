import {Listener, Element} from "../../polymer/Annotations";
import {PolymerElement} from "../../polymer/PolymerElement";
import {GtButton} from "../widgets/GtButton";
import {View} from "../../client/router/View";

@View("apply", () => [{title: "Apply", link: "/apply", active: true}])
@Element({
	selector: "gt-apply-redirect",
	template: "/assets/views/apply.html",
	dependencies: [GtButton]
})
export class GtApplyRedirect extends PolymerElement {
	@Listener("apply.click")
	private ApplyNow() {
		document.location.href = "http://www.fs-guild.net/tools/#/apply";
	}
}
