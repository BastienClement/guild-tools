import { Element, Property, PolymerElement } from "elements/polymer";

@Element("gt-dialog", "/assets/imports/widgets.html")
export class GtDialog extends PolymerElement {
	/**
	 * The dialog title
	 */
	public label: string;
	
	/**
	 * If defined, the .with-modal class will not be added
	 */
	@Property({ type: Boolean })
	public noModal: boolean;
	
	/**
	 * Define the dialog as scrollable
	 */
	@Property({ value: false, observer: "scrollable-changed" })
	public scrollable: boolean;
	
	// <gt-dialog-actions>
	private actions: Element;
	
	private created() {
		// Detach the <gt-dialog-actions> element from the content
		if (this.actions = this.$$("gt-dialog-actions")) {
			this.actions.parentNode.removeChild(this.actions);
		}
	}
	
	private ready() {
		// If we have picked up a <gt-dialog-actions> element, insert it back
		if (this.actions) {
			Polymer.dom(this.$["layout"]).appendChild(this.actions);
		}	
	}
	
	private attached() {
		if (!this.noModal) document.body.classList.add("with-modal");
	}
	
	private detached() {
		if (!this.noModal) document.body.classList.remove("with-modal");
	}
	
	private "scrollable-changed" () {
		//console.log(arguments);
	}
}

@Element("gt-button", "/assets/imports/widgets.html")
export class GtButton extends PolymerElement {
	
}

@Element("gt-dialog-actions", "/assets/imports/widgets.html")
export class GtDialogActions extends PolymerElement {
	private ready() {
		for (let child of this.getContentChildren().slice(1)) {
			child.classList.add("remove-right-margin");
		}
	}
}
