import { Element, Dependencies, Property, Listener, PolymerElement } from "elements/polymer";
import * as Widget from "elements/widgets";
import { Deferred } from "utils/deferred";

@Element("gt-login", "/assets/imports/loading.html")
@Dependencies(Widget.GtDialog)	
export class GtLogin extends PolymerElement {
	// Deferred to resolve with user credentials
	@Property({ observer: "credentials-updated" })
	credentials: Deferred<[string, string]>;
	
	created() {
	}
	
	attached() {
		const dialog: Widget.GtDialog = this.$.loginDialog;
		dialog.show();
	}
	
	detached() {
	}
	
	private "credentials-updated" () {
	}
	
	@Listener("dialog-action")
	private "on-dialog-action" (e: Event, action: string) {
		console.log(action, e);
		e.preventDefault();
	}
}
