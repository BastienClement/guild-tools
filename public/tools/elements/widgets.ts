import { Element, Property, Listener, Dependencies, PolymerElement, PolymerEvent } from "elements/polymer";

/**
 * Clickable button
 */
@Element("gt-button", "/assets/imports/widgets.html")
export class GtButton extends PolymerElement {
	/**
	 * Disable the button, prevent event triggering
	 */
	@Property({ type: Boolean, reflectToAttribute: true })
	public disabled: boolean;
	
	/**
	 * Filter click and tap event if the element is disabled
	 */
	@Listener("click", "tap")
	private "event-filter" (e: Event) {
		if (this.disabled) return this.stopEvent(e);
	}
}

/**
 * Dummy element that calls GtDialog#addAction()
 */
@Element("gt-dialog-action")
export class GtDialogAction extends PolymerElement {
	private attached() {
		this.host(GtDialog).addAction(this.node.textContent);
		this.node.parentNode.removeChild(this);
	}
}

/**
 * Modal dialog
 */
@Element("gt-dialog", "/assets/imports/widgets.html")
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
	private actions: string[];
	
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
	}
	
	/**
	 * Hide this dialog
	 */
	public hide() {
		if (!this.noModal) document.body.classList.remove("with-modal");
		Polymer.dom(this).classList.remove("slide-in");
		Polymer.dom.flush();
		Polymer.dom(this).classList.add("slide-out");
	}
	
	/**
	 * Add a new action button
	 */
	public addAction(label: string) {
		this.push("actions", label);
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
	private "handle-action" (e: PolymerEvent<{ item: string; }>) {
		this.fire("dialog-action", e.model.item);
		this.locked = true;
	}
}
