import {Element} from "../../polymer/Annotations";
import {PolymerElement} from "../../polymer/PolymerElement";

/**
 * Type of DOM Nodes
 */
const enum NodeType {
	DOCUMENT_FRAGMENT_NODE = 11
}

/**
 * Alias for mouse event listener used by tooltip
 */
export type FloatingEventListener = (e: MouseEvent) => void;

function is_DocumentFragment(node: Node): node is DocumentFragment {
	return node && node.nodeType == NodeType.DOCUMENT_FRAGMENT_NODE;
}

// ============================================================================

/**
 * Dummy placeholder that notifies its owner when detached
 */
@Element({ selector: "gt-floating-placeholder" })
export class GtFloatingPlaceholder extends PolymerElement {
	private detached() {
		this.fire("detached");
	}
}

// ============================================================================

export interface FloatingInterface extends PolymerElement {
	visible: boolean;
	parent: Node;
	show(e: MouseEvent): void;
	hide(): void;
}

/**
 * Basic class for Tooltips and Contextual Menus.
 */
export class FloatingItem {
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
		this.owner.fire("floating-show");

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

		this.owner.fire("floating-hide");
		this.transition = false;
	}
}
