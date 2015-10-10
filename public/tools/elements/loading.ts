import { Element, Dependencies, Property, Listener, PolymerElement } from "elements/polymer";
import * as Widget from "elements/widgets";
import { GtBox } from "elements/box";
import { GtDialog } from "elements/dialog";
import { Deferred } from "utils/deferred";

/**
 * Handle the user login credentials request
 */
@Element("gt-login", "/assets/imports/loading.html")
@Dependencies(GtDialog, GtBox, Widget.GtInput, Widget.GtButton)
export class GtLogin extends PolymerElement {
	/**
	 * Deferred to resolve with user credentials
	 */
	@Property({ observer: "CredentialsUpdated" })
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
		setTimeout(() => dialog.show(), 1000);
	}

	/**
	 * Toggle dialog locked state based on credentials deferred availability
	 */
	private CredentialsUpdated() {
		const dialog: GtDialog = this.$.dialog;
		this.$.btn.disabled = !this.credentials;
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
	private SaveUsername() {
		localStorage.setItem("login.username", this.$.username.value);
	}

	/**
	 * Handle form submit
	 */
	@Listener("form.submit")
	private FormSubmit() {
		this.credentials.resolve([this.$.username.value, this.$.password.value]);
		this.credentials = null;
	}
	
	@Listener("btn.click")
	private BtnClick() {
		this.$.form.submit();
	}
}
