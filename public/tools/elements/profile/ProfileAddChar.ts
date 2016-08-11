import {Element, Inject, Property} from "../../polymer/Annotations";
import {GtInput} from "../widgets/GtInput";
import {GtForm} from "../widgets/GtForm";
import {GtBox} from "../widgets/GtBox";
import {PolymerElement} from "../../polymer/PolymerElement";
import {ProfileService} from "./../../services/profile/ProfileService";
import {Char} from "../../services/roster/RosterService";

///////////////////////////////////////////////////////////////////////////////
// <profile-add-char>

@Element({
	selector: "profile-add-char",
	template: "/assets/views/profile.html",
	dependencies: [GtBox, GtForm, GtInput]
})
export class ProfileAddChar extends PolymerElement {
	@Inject
	private profile: ProfileService;

	// The owner of the newly added char
	@Property
	public owner: number = this.app.user.id;

	private server: string;
	private name: string;
	private role: string;

	private char: Char;
	private load_done = false;
	private inflight = false;

	// Check if the user can load a character (required fields filled and not already loaded)
	@Property({computed: "server name load_done"})
	private get canLoad(): boolean {
		return !!this.server && !!this.name && !this.load_done;
	}

	// Load the character
	private async load() {
		if (!this.canLoad) return;
		this.load_done = true;

		let input: GtInput = this.$.name;
		input.error = null;

		try {
			// Check if the character is already registered to someone in the database
			let available = await this.profile.checkAvailability(this.server, this.name);
			if (!available) {
				throw new Error("This character is already registered");
			}

			// Fetch the char from Battle.net
			let char = await this.profile.fetchChar(this.server, this.name);

			// Change the dialog background
			let img = document.createElement("img");
			let background: HTMLDivElement = this.$.background;
			img.src = "http://eu.battle.net/static-render/eu/" + char.thumbnail.replace("avatar", "profilemain");
			Polymer.dom(background).appendChild(img);
			//img.onload = () => {
				this.char = char;
				this.role = char.role;
				img.classList.add("loaded");
			//};
		} catch (e) {
			input.error = e.message;
			input.value = "";
			input.focus();
			this.load_done = false;
		}
	}

	// Role selected
	private roleClicked(e: MouseEvent) {
		let img = <HTMLImageElement> e.target;
		this.role = img.getAttribute("role");
	}

	// Show the add-char dialog
	public show() {
		// Reset fields values
		this.name = "";
		this.server = "";
		this.char = null;
		this.load_done = false;

		// Remove old backgrounds
		let background: HTMLDivElement = this.$.background;
		let imgs = background.querySelectorAll<HTMLImageElement>("img:not([default])");
		for (let i = 0; i < imgs.length; i++) {
			imgs[i].remove();
		}

		// Show the dialog
		this.$.dialog.show();

		// Focus the server field
		this.$.name.focus();
	}

	@Property({computed: "char inflight"})
	private get confirmDisabled(): boolean {
		return !this.char || this.inflight;
	}

	// Add the character to the user account
	private async confirm() {
		let server = this.$.server.value;
		let name = this.$.name.value;
		let role = this.role;
		let owner = this.owner;

		this.inflight = true;
		try {
			await this.profile.registerChar(server, name, role, owner);
			this.close();
		} catch (e) {
			let input: GtInput = this.$.name;
			input.error = e.message;
			input.value = "";
			input.focus();
			this.load_done = false;
		} finally {
			this.inflight = false;
		}
	}

	// Close the dialog
	public close() {
		this.$.dialog.hide();
	}
}
