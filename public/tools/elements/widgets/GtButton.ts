import {PolymerElement} from "../../polymer/PolymerElement";
import {Property, Listener, Element} from "../../polymer/Annotations";
import {GtForm} from "./GtForm";

/**
 * Clickable button
 */
@Element({
	selector: "gt-button",
	template: "/assets/imports/widgets.html",
	dependencies: [GtForm]
})
export class GtButton extends PolymerElement {
	/**
	 * Disable the button, prevent event triggering
	 */
	@Property({reflect: true})
	public disabled: boolean;

	/**
	 * If set, clicking the button will trigger the submit event in the enclosing GtForm
	 */
	@Property
	public submit: boolean;

	/**
	 * If set, clicking the button will trigger navigation
	 */
	@Property
	public goto: string;

	/**
	 * Click handler
	 */
	@Listener("click", "tap")
	private ClickEvent(e: Event) {
		// Button is disabled
		if (this.disabled) {
			e.stopImmediatePropagation();
			e.preventDefault();
			return false;
		}

		// Button is a link
		if (this.goto) {
			this.app.router.goto(this.goto);
			return;
		}

		// Button is a form submit action
		if (this.submit) {
			const form = this.host(GtForm);
			if (form) form.submit();
		}
	}
}
