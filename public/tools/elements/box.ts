import { Element, Property, Dependencies, PolymerElement } from "elements/polymer";

@Element("gt-box", "/assets/imports/box.html")
export class GtBox extends PolymerElement {
	@Property(String)
	public heading: string;

	@Property(String)
	public scrollable: string;

	@Property({ computed: "heading" })
	private get "has-heading"(): boolean {
		return !!this.heading;
	}
}

@Element("gt-alert", "/assets/imports/box.html")
@Dependencies(GtBox)
export class GtAlert extends PolymerElement {
	@Property(String)
	public icon: String;
}
