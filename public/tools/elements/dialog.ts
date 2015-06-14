import { Element, Property, Listener, Dependencies, PolymerElement, PolymerEvent } from "elements/polymer";
import { GtButton, GtForm } from "elements/widgets"
import { Queue } from "utils/queue"

/**
 * Dummy element that calls GtDialog#addAction()
 */
@Element("gt-dialog-action")
export class GtDialogAction extends PolymerElement {
	/**
	 * The button icon
	 */
	@Property({ type: String })
	public icon: string;
	
	/**
	 * If set to true, clicking this action button will submit the
	 * <gt-form> contained in the dialog
	 */
	@Property({ type: Boolean })
	public submit: boolean;
	
	private attached() {
		this.host(GtDialog).addAction(this.node.textContent, this.icon, this.submit);
		Polymer.dom(this.node.parentNode).removeChild(this);
	}
}

/**
 * Modal dialog
 */
@Element("gt-dialog", "/assets/imports/dialog.html")
@Dependencies(GtButton, GtDialogAction)	
export class GtDialog extends PolymerElement {
	/**
	 * Pending modal dialogs
	 */
	private static queue = new Queue<GtDialog>();
	
	/**
	 * A modal window is currently visible
	 */
	private static visible = false;
	
	/**
	 * The dialog title
	 */
	@Property({ type: String })
	public heading: string;
	
	/**
	 * Available actions
	 */
	@Property({ type: Array, value: [] })
	private actions: [string, string, boolean][];
	
	/**
	 * If defined, the .with-modal class will not be added
	 * TODO: rework
	 */
	@Property({ type: Boolean })
	public noModal: boolean;
	
	/**
	 * A sticky dialog does not autoclose
	 */
	@Property({ type: Boolean })
	public sticky: boolean;
	
	/**
	 * Lock the dialog actions
	 */
	@Property({ type: Boolean })
	public locked: boolean;
	
	/**
	 * Show the dialog if no other modal is opened
	 */
	public show(force: boolean = false) {
		// Enqueue the modal if another one is still open
		if (GtDialog.visible && !force) {
			if (!GtDialog.queue.contains(this)) {
				GtDialog.queue.enqueue(this);
			}
			return;
		}
		
		if (!this.noModal) document.body.classList.add("with-modal");
		GtDialog.visible = true;
		Polymer.dom(this).classList.add("slide-in");
		this.fire("show");
	}
	
	/**
	 * Hide this dialog
	 */
	public hide() {
		if (!this.noModal) document.body.classList.remove("with-modal");
		Polymer.dom(this).classList.remove("slide-in");
		Polymer.dom.flush();
		Polymer.dom(this).classList.add("slide-out");
		this.fire("hide");
		
		if (GtDialog.queue.length() > 0) {
			GtDialog.queue.dequeue().show(true);
		} else {
			GtDialog.visible = false;
		}
	}
	
	/**
	 * Add a new action button
	 */
	public addAction(label: string, icon: string, submit: boolean) {
		this.push("actions", [label, icon, submit]);
	}
	
	/**
	 * Remove all previously registered actions
	 */
	public clearActions() {
		this.actions = [];
	}
	
	/**
	 * Handle action button click
	 */
	private "handle-action"(e: PolymerEvent<{ item: [string, string, boolean]; }>) {
		if (e.model.item[2]) {
			const form = this.node.querySelector<GtForm>("gt-form");
			form.submit();
			if (form.failed) return;
		} else {
			this.fire("action", e.model.item[0]);
		}	
		this.locked = true;
	}
}
