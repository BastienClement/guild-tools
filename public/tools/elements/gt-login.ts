import { polymer, property, PolymerElement } from "elements/polymer";
import { Deferred } from "utils/deferred";

@polymer("gt-login")
export class GtLogin extends PolymerElement {
	@property({ value: null, observer: "credentials-available" })
	credentials: Deferred<[string, string]>;
	
	created() {
		console.log(this, "created");
	}
	
	attached() {
		console.log(this, "attached");
	}
	
	"credentials-available"() {
		console.log(this, arguments);
	}
}
