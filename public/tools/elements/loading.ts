import { Element, Dependencies, Property, PolymerElement } from "elements/polymer";
import { Deferred } from "utils/deferred";

@Element("gt-login", "/assets/imports/loading.html")
export class GtLogin extends PolymerElement {
	// Deferred to resolve with user credentials
	@Property({ value: null, observer: "credentials-updated" })
	credentials: Deferred<[string, string]>;
	
	created() {
		console.log(this, "created");
	}
	
	attached() {
		console.log(this, "attached");
	}
	
	private "credentials-updated" () {
		console.log(this, arguments);
	}
	
	static test() { }
}
