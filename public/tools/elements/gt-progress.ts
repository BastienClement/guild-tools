import { PolymerElement, polymer } from "elements/polymer";

@polymer("gt-progress")
export class GtProgress extends PolymerElement {
	created() {
		console.log(this, "created");
	}
}
