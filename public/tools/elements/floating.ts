import { Element, Property, Listener, Dependencies, PolymerElement } from "elements/polymer";

/**
 * Type of DOM Nodes
 */
const enum NodeType {
	DOCUMENT_FRAGMENT_NODE = 11
}

/**
 * Alias for mouse event listener used by tooltip
 */
type EventListener = (e: MouseEvent) => void;

function is_DocumentFragment(node: Node): node is DocumentFragment {
	return node && node.nodeType == NodeType.DOCUMENT_FRAGMENT_NODE;
}

// ============================================================================

/**
 * Dummy placeholder that notifies its owner when detached
 */
@Element("gt-floating-placeholder")
export class GtFloatingPlaceholder extends PolymerElement {
	private detached() {
		this.fire("detached");
	}
}

// ============================================================================

interface FloatingInterface extends PolymerElement {
	visible: boolean;
	parent: Node;
	show(e: MouseEvent): void;
	hide(): void;
}

/**
 * Basic class for Tooltips and Contextual Menus.
 */
class FloatingItem {
	constructor(
		private owner: FloatingInterface,
		private node: ShadyDOM) {
	}

	// The floating item is transitioning between visible and hidden state
	private transition = false;

	// The floating item's placeholder while visible
	private placeholder: HTMLElement = <any> new GtFloatingPlaceholder();

	// Listener for placeholder detached
	private detached_listener: () => void;

	/**
	 * Floating got attached in the document
	 */
	public attached(): boolean {
		if (this.transition) return;

		// Search parent element for binding enter and leave event.
		this.owner.parent = this.node.parentNode;

		// Take host element if we have a ShadowRoot
		let parent = this.owner.parent;
		if (is_DocumentFragment(parent)) {
			this.owner.parent = parent.host;
		}

		this.detached_listener = () => this.hide();
		return <any> this.owner.parent;
	}

	/**
	 * Floating was detached from the document
	 */
	public detached() {
		if (this.transition || !this.owner.parent) return false;
		this.hide();
		return true;
	}

	/**
	 * Show the floating
	 */
	public async show(e: MouseEvent) {
		if (this.owner.visible) return;
		this.transition = true;

		Polymer.dom(this.node.parentNode).insertBefore(this.placeholder, this.owner);
		Polymer.dom(document.body).appendChild(this.owner);

		this.placeholder.addEventListener("detached", this.detached_listener);

		this.owner.visible = true;
		this.transition = false;

		await microtask;
		this.owner.show(e);
	}

	/**
	 * Hide the floating
	 */
	public hide() {
		if (!this.owner.visible) return;
		this.transition = true;
		this.owner.visible = false;

		this.placeholder.removeEventListener("detached", this.detached_listener);

		Polymer.dom(this.placeholder.parentNode).insertBefore(this.owner, this.placeholder);
		this.placeholder.parentNode.removeChild(this.placeholder);

		this.transition = false;
	}
}

// ============================================================================

/**
 * Tooltip
 */
@Element("gt-tooltip", "/assets/imports/floating.html")
export class GtTooltip extends PolymerElement {
	@Property({ reflect: true })
	public visible: boolean;
	public parent: Node;

	// The floating item
	private floating: FloatingItem;

	// Event listeners
	// Actual function creation must be deferred until the attached event
	// or we will bind this to the placeholder object used during Element initialization.
	private enter_listener: EventListener;
	private leave_listener: EventListener;
	private move_listener: EventListener;

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

	@Listener("dom-change")
	private update() {
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

	@Property({ observer: "UpdateWidth" })
	public width: number = 300;

	// Max width updated
	private UpdateWidth() {
		this.style.maxWidth = this.width + "px";
	}
}

// ============================================================================

/**
 * Context menu
 */
@Element("gt-context-menu", "/assets/imports/floating.html")
export class GtContextMenu extends PolymerElement {
	@Property({ reflect: true })
	public visible: boolean;
	public parent: Node;

	@Property public passive: boolean = false;
	@Property public useclick: boolean = false;

	// The floating item
	private floating: FloatingItem;

	// Event listeners
	// Actual function creation must be deferred until the attached event
	// or we will bind this to the placeholder object used during Element initialization.
	private context_listener: EventListener;
	private close_listener: EventListener;
	private stop_listener: EventListener;

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

	private update() {
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

	public hide() {
		document.removeEventListener("mousedown", this.close_listener);
		document.removeEventListener("click", this.close_listener);
		this.removeEventListener("mousedown", this.stop_listener);
	}

	public open(event: MouseEvent) {
		this.floating.show(event);
	}
}
