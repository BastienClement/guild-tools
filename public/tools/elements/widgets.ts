import { Element, Property, Listener, Dependencies, PolymerElement, PolymerEvent } from "elements/polymer";

@Element("gt-form")
export class GtForm extends PolymerElement {
	/**
	 * Trigger the submit event
	 */
	public submit() {
		for (let input of this.node.querySelectorAll("gt-input[required]").map(n => Polymer.cast(n, GtInput))) {
			if (input.value.match(/^\s*$/)) {
				input.error = "This field is required";
				input.focus();
				return;
			}
		}
		this.fire("submit");
	}
}

/**
 * Text input
 */
@Element("gt-input", "/assets/imports/widgets.html")
@Dependencies(GtForm)
export class GtInput extends PolymerElement {
	/**
	 * Disable the input
	 */
	@Property({ type: Boolean, reflectToAttribute: true })
	public disabled: boolean;
	
	/**
	 * Prevent form submission if empty
	 */
	@Property({ type: Boolean, reflectToAttribute: true })
	public required: boolean;
	
	/**
	 * Input type "text", "password"
	 */
	@Property({ type: String, value: "text" })
	public type: string;
	
	/**
	 * Error message
	 */
	@Property({ type: String, value: "", reflectToAttribute: true })
	public error: string;
	
	/**
	 * Proxy to input value
	 */
	@Property({ type: String })
	get value(): string { return this.$.input.value; }
	set value(v: string) { this.$.input.value = v; }
	
	/**
	 * Focus the input
	 */
	public focus() {
		this.$.input.focus();
	}
	
	/**
	 * Blur the input
	 */
	public blur() {
		this.$.input.blur();
	}
	
	/**
	 * Relay input change event
	 */
	@Listener("input.change")
	private "input-changed"(e: Event) {
		this.stopEvent(e);
		this.fire("change", this.value);
	}
	
	/**
	 * Catch Enter-key presses in the input and forward to the form
	 */
	@Listener("input.keypress")
	private "input-keypressed"(e: KeyboardEvent) {
		if (e.keyCode == 13) {
			e.preventDefault();
			const form = this.host(GtForm);
			if (form) {
				this.$.input.blur();
				form.submit();
			}
		}
	}
	
	/**
	 * Reflect input focused state to the outer <label> element
	 */
	@Listener("input.focus") private "input-focused"() { this.$.outer.classList.add("focus"); }
	@Listener("input.blur") private "input-blured"() { this.$.outer.classList.remove("focus"); }
}


/**
 * Clickable button
 */
@Element("gt-button", "/assets/imports/widgets.html")
@Dependencies(GtForm)
export class GtButton extends PolymerElement {
	/**
	 * Disable the button, prevent event triggering
	 */
	@Property({ type: Boolean, reflectToAttribute: true })
	public disabled: boolean;
	
	/**
	 * If set, clicking the button will trigger the submit event
	 * in the enclosing GtForm
	 */
	@Property({ type: Boolean })
	public submit: boolean;
	
	/**
	 * Filter click and tap event if the element is disabled
	 */
	@Listener("click", "tap")
	private "event-filter"(e: Event) {
		if (this.disabled) return this.stopEvent(e);
		if (this.submit) {
			const form = this.host(GtForm);
			if (form) form.submit();
		}	
	}
}

/**
 * Dummy element that calls GtDialog#addAction()
 */
@Element("gt-dialog-action")
export class GtDialogAction extends PolymerElement {
	/**
	 * The button icon
	 */
	@Property({ type: String })
	public icon: string;
	
	private attached() {
		this.host(GtDialog).addAction(this.node.textContent, this.icon);
		Polymer.dom(this.node.parentNode).removeChild(this);
	}
}

/**
 * Modal dialog
 */
@Element("gt-dialog", "/assets/imports/widgets.html")
@Dependencies(GtButton, GtDialogAction)	
export class GtDialog extends PolymerElement {
	/**
	 * The dialog title
	 */
	@Property({ type: String })
	public heading: string;
	
	/**
	 * Available actions
	 */
	@Property({ type: Array, value: [] })
	private actions: [string, string][];
	
	/**
	 * If defined, the .with-modal class will not be added
	 * TODO: rework
	 */
	@Property({ type: Boolean })
	public noModal: boolean;
	
	/**
	 * A sticky dialog does not autoclose
	 */
	@Property({ type: Boolean })
	public sticky: boolean;
	
	/**
	 * Lock the dialog actions
	 */
	@Property({ type: Boolean })
	public locked: boolean;
	
	/**
	 * Show the dialog if no other modal is opened
	 */
	public show() {
		if (!this.noModal) document.body.classList.add("with-modal");
		Polymer.dom(this).classList.add("slide-in");
		this.fire("show");
	}
	
	/**
	 * Hide this dialog
	 */
	public hide() {
		if (!this.noModal) document.body.classList.remove("with-modal");
		Polymer.dom(this).classList.remove("slide-in");
		Polymer.dom.flush();
		Polymer.dom(this).classList.add("slide-out");
		this.fire("hide");
	}
	
	/**
	 * Add a new action button
	 */
	public addAction(label: string, icon: string) {
		this.push("actions", [label, icon]);
	}
	
	/**
	 * Remove all previously registered actions
	 */
	public clearActions() {
		this.actions = [];
	}
	
	/**
	 * Handle action button click
	 */
	private "handle-action"(e: PolymerEvent<{ item: string; }>) {
		this.fire("action", e.model.item[0]);
		this.locked = true;
	}
}
