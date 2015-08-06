import { Element, Property, Listener, Dependencies, PolymerElement, PolymerEvent } from "elements/polymer";

@Element("gt-form")
export class GtForm extends PolymerElement {
	/**
	 * Indicate an error during form validation
	 */
	@Property({ type: Boolean })
	public failed: boolean;

	/**
	 * Trigger the submit event
	 */
	public submit() {
		for (let input of this.node.querySelectorAll("gt-input[required]").map(n => Polymer.cast(n, GtInput))) {
			if (input.value.match(/^\s*$/)) {
				input.error = "This field is required";
				input.focus();
				this.failed = true;
				return;
			} else {
				input.error = null;
			}
		}
		this.failed = false;
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
	@Property({ type: String, value: null, reflectToAttribute: true, notify: true })
	public error: string;

	/**
	 * Proxy to input value
	 */
	@Property({ type: String, notify: true })
	public value: string;

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
