import { Element, Dependencies, Property, Listener, PolymerElement } from "elements/polymer";
import * as Widget from "elements/widgets";
import { Deferred } from "utils/deferred";

/**
 * Handle the user login credentials request
 */
@Element("gt-login", "/assets/imports/loading.html")
@Dependencies(Widget.GtDialog, Widget.GtInput)	
export class GtLogin extends PolymerElement {
	/**
	 * Deferred to resolve with user credentials
	 */
	@Property({ observer: "credentials-updated" })
	credentials: Deferred<[string, string]>;
	
	/**
	 * Auto show dialog when attached
	 */
	private attached() {
		this.$.username.value = localStorage.getItem("login.username") || "";
		const dialog: Widget.GtDialog = this.$.dialog;
		dialog.show();
	}
	
	/**
	 * Toggle dialog locked state based on credentials deferred availability
	 */
	private "credentials-updated"() {
		const dialog: Widget.GtDialog = this.$.dialog;
		dialog.locked = !this.credentials;
	}
	
	/**
	 * Automatically focus the correct field when the dialog is shown
	 */
	@Listener("dialog.show")
	private "dialog-shown"() {
		const input: Widget.GtInput = (this.$.username.value) ? this.$.password : this.$.username;
		input.focus();
	}
	
	/**
	 * Save the username to local storage
	 */
	@Listener("username.change")
	private "save-username"(e: Event, username: string) {
		localStorage.setItem("login.username", username);
	}
	
	/**
	 * Handle dialog-button clicks
	 */
	@Listener("dialog.action", "form.submit")
	private "on-submit"(e: Event) {
		console.log(this.$.username.value, this.$.password.value);
		this.credentials.resolve([this.$.username.value, this.$.password.value]);
		this.credentials = null;
	}
}
