import { Element, Property, Listener, Dependencies, PolymerElement, PolymerEvent } from "elements/polymer";
import { GtButton } from "elements/widgets"

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
	
	private attached() {
		this.host(GtDialog).addAction(this.node.textContent, this.icon);
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
	 * The dialog title
	 */
	@Property({ type: String })
	public heading: string;
	
	/**
	 * Available actions
	 */
	@Property({ type: Array, value: [] })
	private actions: [string, string][];
	
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
	public show() {
		if (!this.noModal) document.body.classList.add("with-modal");
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
	}
	
	/**
	 * Add a new action button
	 */
	public addAction(label: string, icon: string) {
		this.push("actions", [label, icon]);
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
	private "handle-action"(e: PolymerEvent<{ item: string; }>) {
		this.fire("action", e.model.item[0]);
		this.locked = true;
	}
}
