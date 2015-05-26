import { polymer } from "elements/polymer";
import { Deferred } from "utils/deferred";

@polymer("gt-login")
export class GtLogin {
	credentials: Deferred<[string, string]>;
	
	created() {
		console.log(this, "created");
	}
}
