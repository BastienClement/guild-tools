import {View} from "../../client/router/View";
import {GtBox} from "../widgets/GtBox";
import {PolymerElement} from "../../polymer/PolymerElement";
import {Element} from "../../polymer/Annotations";

@View("about", () => [{title: "About", link: "/about", active: true}])
@Element({
	selector: "gt-about",
	template: "/assets/views/about.html",
	dependencies: [GtBox]
})
export class GtAbout extends PolymerElement {
	private version = this.app.server.version.substr(0, 7);
}
