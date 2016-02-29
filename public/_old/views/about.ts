import { Element, Dependencies, PolymerElement } from "elements/polymer";
import { View } from "elements/app";
import { GtBox } from "elements/box";

@View("about", () => [{ title: "About", link: "/about", active: true }])
@Element("gt-about", "/assets/views/about.html")
@Dependencies(GtBox)
export class GtAbout extends PolymerElement {
	private version = this.app.server.version.substr(0, 7);
}
