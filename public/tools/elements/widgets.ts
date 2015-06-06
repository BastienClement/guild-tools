import { Element, Property, PolymerElement } from "elements/polymer";

@Element("gt-dialog", "/assets/imports/widgets.html")
export class GtDialog extends PolymerElement {
	//@Property
	public title: string;
	
	attached() {
		console.log(this.title, typeof this.title);
	}
}

@Element("gt-button", "/assets/imports/widgets.html")
export class GtButton extends PolymerElement {
	
}

@Element("gt-button-container", "/assets/imports/widgets.html")
export class GtButtonContainer extends PolymerElement {
	
}
