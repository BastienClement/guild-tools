import { Element, Property, Listener, Dependencies, PolymerElement, PolymerEvent } from "elements/polymer";

@Element("gt-button", "/assets/imports/widgets.html")
export class GtButton extends PolymerElement {
	@Property({ type: Boolean, reflectToAttribute: true })
	public disabled: boolean;
	
	@Listener("click")
	private "on-click" (e: Event) {
		if (!this.disabled) this.fire("button-click");
	}
}

@Element("gt-dialog-action")
export class GtDialogAction extends PolymerElement {
	private attached() {
		const self = Polymer.dom(this);
		const label = self.textContent;
		
		Polymer.dom(self.parentNode).removeChild(this);
		this.host<GtDialog>().addAction(label);
	}
}

@Element("gt-dialog", "/assets/imports/widgets.html")
@Dependencies(GtButton, GtDialogAction)	
export class GtDialog extends PolymerElement {
	// The dialog title
	@Property({ type: String })
	public heading: string;
	
	// Available actions
	@Property({ type: Array, value: [] })
	private actions: string[];
	
	// If defined, the .with-modal class will not be added
	@Property({ type: Boolean })
	public noModal: boolean;
	
	// Lock the dialog actions
	@Property({ type: Boolean })
	public locked: boolean;
	
	public show() {
		if (!this.noModal) document.body.classList.add("with-modal");
		Polymer.dom(this).classList.add("slide-in");
	}
	
	public hide() {
		if (!this.noModal) document.body.classList.remove("with-modal");
		Polymer.dom(this).classList.remove("slide-in");
		Polymer.dom.flush();
		Polymer.dom(this).classList.add("slide-out");
	}
	
	public addAction(label: string) {
		this.push("actions", label);
	}
	
	public performAction(e: PolymerEvent<{ item: string; }>) {
		this.fire("dialog-action", e.model.item);
		this.locked = true;
	}
}
