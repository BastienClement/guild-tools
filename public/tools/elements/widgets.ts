import { Element, Property, Listener, Dependencies, PolymerElement } from "elements/polymer";

@Element("gt-form")
export class GtForm extends PolymerElement {
	/**
	 * Indicate an error during form validation
	 */
	@Property
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
	@Property({ reflect: true })
	public disabled: boolean;

	/**
	 * Prevent form submission if empty
	 */
	@Property({ reflect: true })
	public required: boolean;

	/**
	 * Input type "text", "password"
	 */
	@Property
	public type: string = "text";

	/**
	 * Error message
	 */
	@Property({ reflect: true, notify: true })
	public error: string = null;

	/**
	 * Proxy to input value
	 */
	@Property({ notify: true })
	public value: string = "";

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
	private InputKeypress(e: KeyboardEvent) {
		this.value = this.$.input.value;
		if (e.keyCode == 13) {
			e.preventDefault();
			const form = this.host(GtForm);
			if (form) {
				this.blur();
				form.submit();
			}
		}
	}

	/**
	 * Reflect input focused state to the outer <label> element
	 */
	@Listener("input.focus") private InputFocus() { this.toggleAttribute("focused", true); }
	@Listener("input.blur") private InputBlur() { this.toggleAttribute("focused", false); }
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
	@Property({ reflect: true })
	public disabled: boolean;

	/**
	 * If set, clicking the button will trigger the submit event
	 * in the enclosing GtForm
	 */
	@Property
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
