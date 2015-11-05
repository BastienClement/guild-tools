import { Element, Property, Dependencies, PolymerElement } from "elements/polymer";

@Element("gt-box", "/assets/imports/box.html")
export class GtBox extends PolymerElement {
	@Property public heading: string;
	@Property public scrollable: string;

	@Property({ reflect: true })
	public dark: boolean;

	@Property({ computed: "heading" })
	private get "has-heading"(): boolean {
		return !!this.heading;
	}
}

@Element("gt-alert", "/assets/imports/box.html")
@Dependencies(GtBox)
export class GtAlert extends PolymerElement {
	@Property public icon: String;

	@Property({ reflect: true })
	public dark: boolean;
}
