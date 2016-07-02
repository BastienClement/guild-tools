import {Property, Element} from "../../polymer/Annotations";
import {PolymerElement} from "../../polymer/PolymerElement";
import {GtInput} from "./GtInput";

@Element({
	selector: "gt-form"
})
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
		for (let input of this.node.querySelectorAll("gt-input[required]").map(n => <GtInput> n)) {
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
