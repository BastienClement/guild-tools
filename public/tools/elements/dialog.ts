import { Element, Property, Listener, PolymerElement } from "elements/polymer";
import { Queue } from "utils/queue"
import { Deferred } from "utils/deferred"

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
	public sticky: boolean;

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
				return Promise.resolve();
			}
		}

		GtDialog.visible = this;
		this.node.classList.add("visible");
		this.$.slider.classList.add("slide-in");
		Polymer.dom.flush();
		
		this.fire("show");
		return Promise.resolve();
	}

	/**
	 * Hide this dialog
	 */
	public async hide(): Promise<void> {
		let node = this.node.node;
		let defer = new Deferred<void>();
		
		const animation_listener = () => {
			node.removeEventListener("animationend", animation_listener);
			defer.resolve();
			this.close();
		}
		
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
		this.stopEvent(e);
	}
	
	private detached() {
		if (GtDialog.visible === this) {
			this.close();
		}
	}
}
