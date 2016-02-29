import {PolymerElement} from "../../polymer/PolymerElement";
import {Element} from "../../polymer/Element";

@Element({
	selector: "gt-app"
})
export class GtApp extends PolymerElement {
	public view: any = null;

	constructor() {
		super();
		console.log("called constructor");
	}
}
