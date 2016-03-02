import {Element, Property} from "../../polymer/Annotations";
import {PolymerElement} from "../../polymer/PolymerElement";
import {FloatingItem, FloatingEventListener} from "./Floating";

/**
 * Context menu
 */
@Element({
	selector: "gt-context-menu",
	template: "/assets/imports/floating.html"
})
export class GtContextMenu extends PolymerElement {
	@Property({reflect: true})
	public visible: boolean;
	public parent: Node;

	@Property
	public passive: boolean = false;
	@Property
	public useclick: boolean = false;

	// The floating item
	private floating: FloatingItem;

	// Event listeners
	// Actual function creation must be deferred until the attached event
	// or we will bind this to the placeholder object used during Element initialization.
	private context_listener: FloatingEventListener;
	private close_listener: FloatingEventListener;
	private stop_listener: FloatingEventListener;

	// Position of the context menu
	private x: number;
	private y: number;

	private attached() {
		if (!this.floating) {
			this.floating = new FloatingItem(this, this.node);
		}

		if (this.floating.attached()) {
			this.context_listener = (e) => {
				if (e.shiftKey) return;
				this.floating.show(e);
				e.preventDefault();
				e.stopImmediatePropagation();
			};

			this.close_listener = (e) => this.floating.hide();

			this.stop_listener = (e) => e.stopPropagation();

			if (!this.passive) {
				this.parent.addEventListener("contextmenu", this.context_listener);
				if (this.useclick) this.parent.addEventListener("click", this.context_listener);
			}
		}
	}

	private detached() {
		if (this.floating.detached() && !this.passive) {
			this.parent.removeEventListener("contextmenu", this.context_listener);
			if (this.useclick) this.parent.addEventListener("click", this.context_listener);
		}
	}

	public show(e: MouseEvent) {
		document.addEventListener("mousedown", this.close_listener);
		document.addEventListener("click", this.close_listener);
		this.addEventListener("mousedown", this.stop_listener);

		this.x = e.clientX;
		this.y = e.clientY;

		this.update();
	}

	public hide() {
		document.removeEventListener("mousedown", this.close_listener);
		document.removeEventListener("click", this.close_listener);
		this.removeEventListener("mousedown", this.stop_listener);
	}

	private update() {
		if (!this.visible) return;

		let x = this.x - 1;
		let y = this.y - 1;

		let width = this.offsetWidth;
		let height = this.offsetHeight;

		if (x + width + 10 > window.innerWidth) {
			x -= width - 2;
		}

		if (y + height + 10 > window.innerHeight) {
			y -= height - 2;
		}

		this.style.left = x + "px";
		this.style.top = y + "px";
	}

	public open(event: MouseEvent) {
		this.floating.show(event);
	}
}
