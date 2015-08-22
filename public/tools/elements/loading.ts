import { Element, Dependencies, Property, Listener, PolymerElement } from "elements/polymer";
import * as Widget from "elements/widgets";
import { GtDialog } from "elements/dialog";
import { Deferred } from "utils/deferred";

/**
 * Handle the user login credentials request
 */
@Element("gt-login", "/assets/imports/loading.html")
@Dependencies(GtDialog, Widget.GtInput)
export class GtLogin extends PolymerElement {
	/**
	 * Deferred to resolve with user credentials
	 */
	@Property({ observer: "credentials-updated" })
	credentials: Deferred<[string, string]>;

	/**
	 * Deferred to resolve with user credentials
	 */
	@Property({ type: String, value: null })
	error: string;

	/**
	 * Auto show dialog when attached
	 */
	private attached() {
		this.$.username.value = localStorage.getItem("login.username") || "";
		const dialog: GtDialog = this.$.dialog;
		dialog.show();
	}

	/**
	 * Toggle dialog locked state based on credentials deferred availability
	 */
	private "credentials-updated"() {
		const dialog: GtDialog = this.$.dialog;
		dialog.locked = !this.credentials;
	}

	/**
	 * Close the login dialog
	 * Return a promise that will be completed once the dialog is hidden
	 */
	close(): Promise<void> {
		return new Promise<void>((res, rej) => {
			const dialog: GtDialog = this.$.dialog;
			dialog.hide();
			dialog.addEventListener("animationend", () => res());
		});
	}

	/**
	 * Automatically focus the correct field when the dialog is shown
	 */
	@Listener("dialog.show")
	public autofocus() {
		const input: Widget.GtInput = (this.$.username.value) ? this.$.password : this.$.username;
		input.focus();
		input.value = "";
	}

	/**
	 * Save the username to local storage
	 */
	@Listener("username.value-changed")
	private "save-username"() {
		localStorage.setItem("login.username", this.$.username.value);
	}

	/**
	 * Handle form submit
	 */
	@Listener("form.submit")
	private "on-submit"() {
		this.credentials.resolve([this.$.username.value, this.$.password.value]);
		this.credentials = null;
	}
}
