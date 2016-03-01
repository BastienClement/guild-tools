import {Element, Listener} from "../../polymer/Annotations";
import {PolymerElement} from "../../polymer/PolymerElement";

/**
 * <gt-label>
 */
@Element({
	selector: "gt-label",
	template: "/assets/imports/widgets.html"
})
export class GtLabel extends PolymerElement {
	@Listener("click")
	private OnClick(ev: Event) {
		let control: any = this.node.querySelector("gt-input, gt-checkbox");
		if (control && control != ev.target) control.click();
	}

	@Listener("mouseenter")
	private OnEnter(ev: Event) {
		let control: any = this.node.querySelector("gt-input, gt-checkbox");
		if (control) control.setAttribute("hover", true);
	}

	@Listener("mouseleave")
	private OnLeave(ev: Event) {
		let control: any = this.node.querySelector("gt-input, gt-checkbox");
		if (control) control.removeAttribute("hover");
	}
}
