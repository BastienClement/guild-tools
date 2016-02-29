import { Element, Property, Listener, PolymerElement } from "elements/polymer";
import { Queue } from "../utils/Queue"

/**
 * Modal dialog
 */
@Element("gt-dialog", "/assets/imports/dialog.html")
export class GtDialog extends PolymerElement {
	/**
	 * Pending modal dialogs
	 */
	private static queue = new Queue<GtDialog>();

	/**
	 * A modal window is currently visible
	 */
	private static visible: GtDialog = null;

	/**
	 * A sticky dialog does not autoclose if the user click outside it
	 */
	@Property
	public sticky: boolean = false;

	/**
	 * If defined, the dialog will be moved to the <body> element
	 * while visible. This will break CSS scoping but allow it
	 * escape absolute positioning.
	 */
	@Property
	public escape: boolean = false;

	/**
	 * The dialog placeholder while visible
	 */
	private placeholder: Node = null;

	/**
	 * True if the dialog is currently shown
	 */
	public get shown(): boolean {
		return GtDialog.visible === this;
	}

	/**
	 * Show the dialog if no other modal is opened
	 */
	public async show(force: boolean = false): Promise<void> {
		// Enqueue the modal if another one is still open
		if (GtDialog.visible) {
			if (force) {
				await GtDialog.visible.hide();
			} else {
				if (!GtDialog.queue.contains(this)) {
					GtDialog.queue.enqueue(this);
				}
				return;
			}
		}

		if (this.escape) {
			this.placeholder = document.createComment(" dialog placeholder ");
			Polymer.dom(this.node.parentNode).insertBefore(this.placeholder, this);
			Polymer.dom(document.body).appendChild(this);
		}

		GtDialog.visible = this;
		this.node.classList.add("visible");
		this.$.slider.classList.add("slide-in");
		Polymer.dom.flush();

		this.fire("show");
	}

	/**
	 * Hide this dialog
	 */
	public hide(): Promise<void> {
		let node = this.node.node;
		let defer = Promise.defer<void>();

		const animation_listener = () => {
			node.removeEventListener("animationend", animation_listener);
			defer.resolve();
			this.close();
		};

		node.classList.add("fade-out");
		node.addEventListener("animationend", animation_listener);

		return defer.promise;
	}

	private close() {
		let node = this.node.node;

		this.$.slider.classList.remove("slide-in");
		node.classList.remove("visible");
		node.classList.remove("fade-out");

		if (GtDialog.visible == this) {
			GtDialog.visible = null;
		}

		if (this.escape) {
			Polymer.dom(this.placeholder.parentNode).insertBefore(this, this.placeholder);
			this.placeholder.parentNode.removeChild(this.placeholder);
			this.placeholder = null;
		}

		this.fire("hide");

		if (GtDialog.queue.length() > 0) {
			GtDialog.queue.dequeue().show(true);
		}
	}

	@Listener("click")
	private BackgroundClick() {
		if (!this.sticky) this.hide();
	}

	@Listener("content.click")
	private ContentClick(e: Event) {
		e.stopImmediatePropagation();
		e.preventDefault();
	}

	private detached() {
		if (GtDialog.visible === this) {
			this.close();
		}
	}
}
