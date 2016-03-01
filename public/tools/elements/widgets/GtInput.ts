import {Element, Property, Listener} from "../../polymer/Annotations";
import {GtForm} from "./GtForm";
import {PolymerElement} from "../../polymer/PolymerElement";

/**
 * Text input
 */
@Element({
	selector: "gt-input",
	template: "/assets/imports/widgets.html",
	dependencies: [GtForm]
})
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
		if (e.keyCode == 13) {
			e.preventDefault();
			const form = this.host(GtForm);
			if (form) {
				this.blur();
				form.submit();
			}
		}
	}

	@Listener("input.keyup")
	private InputUp(e: KeyboardEvent) {
		this.debounce("value-changed", () => this.value = this.$.input.value, 200);
	}

	public click() {
		this.focus();
	}

	/**
	 * Reflect input focused state to the outer <label> element
	 */
	@Listener("input.focus")
	private InputFocus() {
		this.toggleAttribute("focused", true);
	}

	@Listener("input.blur")
	private InputBlur() {
		this.toggleAttribute("focused", false);
	}
}
