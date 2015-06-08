import { Element, Dependencies, Property, Listener, PolymerElement } from "elements/polymer";
import * as Widget from "elements/widgets";
import { Deferred } from "utils/deferred";

/**
 * Handle the user login credentials request
 */
@Element("gt-login", "/assets/imports/loading.html")
@Dependencies(Widget.GtDialog)	
export class GtLogin extends PolymerElement {
	/**
	 * Deferred to resolve with user credentials
	 */
	@Property({ observer: "credentials-updated" })
	credentials: Deferred<[string, string]>;
	
	/**
	 * Auto show dialog when attached
	 */
	attached() {
		const dialog: Widget.GtDialog = this.$.form;
		dialog.show();
	}
	
	/**
	 * Toggle dialog locked state based on credentials deferred availability
	 */
	private "credentials-updated"() {
		const dialog: Widget.GtDialog = this.$.form;
		dialog.locked = !this.credentials;
	}
	
	/**
	 * Handle dialog-button clicks
	 */
	@Listener("dialog-action")
	private "on-dialog-action"(e: Event, action: string) {
		this.credentials.resolve([this.$.username.value, this.$.password.value]);
		this.credentials = null;
	}
}
