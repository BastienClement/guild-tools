import {PolymerElement} from "../../polymer/PolymerElement";
import {Property, Element} from "../../polymer/Annotations";

@Element({
	selector: "gt-box",
	template: "/assets/imports/box.html"
})
export class GtBox extends PolymerElement {
	@Property
	public heading: string;
	@Property
	public scrollable: string;

	@Property({reflect: true})
	public dark: boolean;
}

@Element({
	selector: "gt-alert",
	template: "/assets/imports/box.html",
	dependencies: [GtBox]
})
export class GtAlert extends PolymerElement {
	@Property public icon: String;

	@Property({ reflect: true })
	public dark: boolean;
}
