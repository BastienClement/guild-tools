import { Element, Dependencies, Property, PolymerElement } from "elements/polymer";
import * as Widget from "elements/widgets";
import { Deferred } from "utils/deferred";

@Element("gt-login", "/assets/imports/loading.html")
@Dependencies(Widget.GtDialog, Widget.GtButton, Widget.GtButtonContainer)	
export class GtLogin extends PolymerElement {
	// Deferred to resolve with user credentials
	@Property({ observer: "credentials-updated" })
	credentials: Deferred<[string, string]>;
	
	created() {
		console.log(this, "created");
	}
	
	attached() {
		console.log(this, "attached");
	}
	
	detached() {
	}
	
	private "credentials-updated" () {
		console.log(this, arguments);
	}
	
	loginInProgress() {
		return true;
	}
	
	login(foo: any) {
		console.log("Login", foo);
	}
}
