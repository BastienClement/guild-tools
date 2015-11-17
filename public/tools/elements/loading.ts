import { Element, Dependencies, Property, Listener, PolymerElement } from "elements/polymer";
import * as Widget from "elements/widgets";
import { GtBox } from "elements/box";
import { GtDialog } from "elements/dialog";

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
	credentials: PromiseResolver<[string, string]>;

	/**
	 * Deferred to resolve with user credentials
	 */
	@Property
	error: string = null;

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
	close(): Promise<any> {
		return new Promise((res, rej) => {
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
