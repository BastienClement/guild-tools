import { PolymerElement, polymer } from "elements/polymer";

@polymer("gt-loader")
export class GtLoader extends PolymerElement {
	created() {
		console.log(this, "created");
	}
}
