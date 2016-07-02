import {PolymerElement} from "../../polymer/PolymerElement";
import {Property, Element} from "../../polymer/Annotations";
import {FloatingItem, FloatingEventListener} from "./Floating";

/**
 * Tooltip
 */
@Element({
	selector: "gt-tooltip",
	template: "/assets/imports/floating.html"
})
export class GtTooltip extends PolymerElement {
	@Property({reflect: true})
	public visible: boolean;
	public parent: Node;

	// The floating item
	private floating: FloatingItem;

	// Event listeners
	// Actual function creation must be deferred until the attached event
	// or we will bind this to the placeholder object used during Element initialization.
	private enter_listener: FloatingEventListener;
	private leave_listener: FloatingEventListener;
	private move_listener: FloatingEventListener;

	// Position of the tooltip
	private x: number;
	private y: number;

	private attached() {
		if (!this.floating) {
			this.floating = new FloatingItem(this, this.node);
		}

		if (this.floating.attached()) {
			this.enter_listener = (e) => this.floating.show(e);
			this.leave_listener = (e) => this.floating.hide();

			this.move_listener = (e) => {
				this.x = e.clientX;
				this.y = e.clientY;
				this.update();
			};

			this.parent.addEventListener("mouseenter", this.enter_listener);
			this.parent.addEventListener("mouseleave", this.leave_listener);
		}
	}

	private detached() {
		if (this.floating.detached()) {
			this.parent.removeEventListener("mouseenter", this.enter_listener);
			this.parent.removeEventListener("mouseleave", this.leave_listener);
		}
	}

	public show(e: MouseEvent) {
		this.parent.addEventListener("mousemove", this.move_listener);
		this.move_listener(e);
	}

	public hide() {
		this.parent.removeEventListener("mousemove", this.move_listener);
	}

	private update() {
		if (!this.visible) return;

		let x = this.x + 10;
		let y = window.innerHeight - this.y + 10;

		let width = this.offsetWidth;
		let height = this.offsetHeight;

		if (x + width + 20 > window.innerWidth) {
			x -= width + 20;
		}

		if (y + height + 20 > window.innerHeight) {
			y -= height + 20;
		}

		this.style.left = x + "px";
		this.style.bottom = y + "px";
	}

	@Property({observer: "UpdateWidth"})
	public width: number = 300;

	// Max width updated
	private UpdateWidth() {
		this.style.maxWidth = this.width + "px";
	}
}
