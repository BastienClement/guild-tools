import {Element, Property, Listener} from "../../polymer/Annotations";
import {PolymerElement} from "../../polymer/PolymerElement";

/**
 * <gt-textarea>
 */
@Element({
	selector: "gt-textarea",
	template: "/assets/imports/widgets.html"
})
export class GtTextarea extends PolymerElement {
	@Property({ notify: true, observer: "ValueChanged" })
	public value: string;

	@Property public disabled: boolean;

	@Listener("textarea.keyup")
	private InputUp(e: KeyboardEvent) {
		this.debounce("value-changed", () => this.value = this.$.textarea.value, 200);
	}

	@Listener("textarea.change")
	private Changed() {
		this.value = this.$.textarea.value;
	}

	private ValueChanged() {
		if (this.$.textarea.value != this.value) {
			this.$.textarea.value = this.value;
		}
	}
}
