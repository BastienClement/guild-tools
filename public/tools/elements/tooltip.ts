import { Element, Property, Listener, PolymerElement } from "elements/polymer";

/**
 * Type of DOM Nodes
 */
const enum NodeType {
	DOCUMENT_FRAGMENT_NODE = 11
}

/**
 * Host attribute is missing from the DocumentFragment object
 */
interface DocumentFragmentHost extends DocumentFragment {
	host: Element
}

/**
 * Alias for mouse event listener used by tooltip
 */
type EventListener = (e: MouseEvent) => void;

/**
 * Dummy placeholder that notifies its owner when detached
 */
@Element("gt-tooltip-placeholder")
export class GtTooltipPlaceholder extends PolymerElement {
	private detached() {
		this.fire("detached");
	}
}

/**
 * Modal dialog
 */
@Element("gt-tooltip", "/assets/imports/tooltip.html")
export class GtTooltip extends PolymerElement {
	// The tooltip is currently visible
	@Property({ reflect: true })
	private visible: boolean = false;

	// The tooltip is transitioning between visible and hidden state
	private transition: boolean = false;

	// The tooltip placeholder while visible
	private placeholder = <HTMLElement> new GtTooltipPlaceholder();

	// The tooltip parent node
	private parent: Node = null;

	// Event listeners
	// Actual function creation must be deferred until the attached event
	// or we will bind this to the placeholder object used during Element initialization.
	private enter_listener: EventListener;
	private leave_listener: EventListener;
	private move_listener: EventListener;

	@Property({ observer: "UpdateWidth" })
	public width: number = 300;

	/**
	 * Tooltip got attached in the document
	 */
	private attached() {
		if (this.transition) return;

		// Search parent element for binding enter and leave event.
		this.parent = this.node.parentNode;
		if (this.parent && this.parent.nodeType == NodeType.DOCUMENT_FRAGMENT_NODE) {
			// Take host element if we have a ShadowRoot
			this.parent = (<DocumentFragmentHost> this.parent).host;
		}

		if (!this.parent) return;

		this.enter_listener = (e) => this.show(e);
		this.leave_listener = (e) => this.hide(e);
		this.move_listener = (e) => this.move(e);

		this.parent.addEventListener("mouseenter", this.enter_listener);
		this.parent.addEventListener("mouseleave", this.leave_listener);
	}

	/**
	 * Tooltip was detached from the document
	 */
	private detached() {
		if (this.transition || !this.parent) return;
		this.hide(null);
		this.parent.removeEventListener("mouseenter", this.enter_listener);
		this.parent.removeEventListener("mouseleave", this.leave_listener);
		this.parent = null;
	}

	/**
	 * Show the tooltip
	 */
	private async show(e: MouseEvent) {
		if (this.visible) return;
		this.transition = true;

		//this.placeholder = document.createComment(" tooltip placeholder ");
		Polymer.dom(this.node.parentNode).insertBefore(this.placeholder, this);
		Polymer.dom(document.body).appendChild(this);

		this.parent.addEventListener("mousemove", this.move_listener);
		this.placeholder.addEventListener("detached", this.leave_listener);

		this.visible = true;
		this.transition = false;

		await microtask;
		this.move_listener(e);
	}

	/**
	 * Hide the tooltip
	 */
	private hide(e: MouseEvent) {
		if (!this.visible) return;
		this.transition = true;
		this.visible = false;

		this.parent.removeEventListener("mousemove", this.move_listener);
		this.placeholder.removeEventListener("detached", this.leave_listener);

		Polymer.dom(this.placeholder.parentNode).insertBefore(this, this.placeholder);
		this.placeholder.parentNode.removeChild(this.placeholder);

		this.transition = false;
	}

	/**
	 * Mouse moved
	 * @param e
	 */
	private move(e: MouseEvent) {
		let self = <HTMLElement> this;

		let x = e.clientX + 10;
		let y = window.innerHeight - e.clientY + 10;

		let width = self.offsetWidth;
		let height = self.offsetHeight;

		if (x + width + 20 > window.innerWidth) {
			x -= width + 20;
		}

		if (y + height + 20 > window.innerHeight) {
			y -= height + 20;
		}

		self.style.left = x + "px";
		self.style.bottom = y + "px";
	}

	/**
	 * Max width updated
	 */
	private UpdateWidth() {
		(<HTMLElement> this).style.maxWidth = this.width + "px";
	}
}
