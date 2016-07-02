import {Element, Property, Listener} from "../../polymer/Annotations";
import {PolymerElement} from "../../polymer/PolymerElement";

/**
 * <gt-checkbox [checked]="foobar">
 * <gt-checkbox [radio]="foobar" value="foobar">
 */
@Element({
	selector: "gt-checkbox",
	template: "/assets/imports/widgets.html"
})
export class GtCheckbox extends PolymerElement {
	/**
	 * Disable this checkbox
	 */
	@Property({ reflect: true })
	public disabled: boolean;

	/**
	 * Current checkbox state
	 */
	@Property({ reflect: true, notify: true })
	public checked: boolean;

	/**
	 * If defined, the checkbox act as a radio button
	 * If the radio button is selected, the value attribute
	 * will be reflected to the this attribute binding.
	 */
	@Property({ reflect: true, notify: true, observer: "RadioChanged" })
	public radio: string;

	/**
	 * The value of this radio button
	 */
	@Property({ reflect: true })
	public value: string;

	/**
	 * The type of value of this radio button
	 */
	private radio_type: string;

	/**
	 * Cast the string value of this radio to the correct
	 * type for the binding
	 */
	private castValue(): any {
		switch (this.radio_type) {
			case "string": return this.value;
			case "number": return Number(this.value);
			case "boolean":
				switch (this.value) {
					case "0":
					case "false":
						return false;
					default:
						return true;
				}
			default:
				throw new Error(`Cannot cast "${this.value}" to ${this.radio_type}`);
		}
	}

	/**
	 * Click generator
	 */
	public click() {
		this.fire("click");
	}

	/**
	 * Click listener
	 */
	@Listener("click")
	private ClickEvent(e: Event) {
		if (this.disabled) {
			e.stopImmediatePropagation();
			e.preventDefault();
			return false;
		}
		else if (this.value) this.radio = this.castValue();
		else this.checked = !this.checked;
	}

	/**
	 * The radio value changed
	 * Check if we need to active the checkbox
	 */
	private RadioChanged() {
		this.radio_type = typeof this.radio;
		this.checked = this.radio == this.value;
	}
}
