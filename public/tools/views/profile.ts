import { Element, Property, Listener, Dependencies, Inject, On, Watch, Bind, PolymerElement } from "elements/polymer";
import { Router, View } from "client/router";
import { Roster, User, Char } from "services/roster";
import { Profile } from "services/profile";
import { GtBox, GtButton, GtDialog, BnetThumb, GtForm, GtInput } from "elements/defs";
import { throttle } from "utils/Deferred";

Router.declareTabs("profile", [
	{ title: "Profile", link: "/profile" }
]);

@Element("profile-user", "/assets/views/profile.html")
@Dependencies(GtBox, BnetThumb)    
export class ProfileUser extends PolymerElement {
	@Property public user: number;
}

@Element("profile-infos", "/assets/views/profile.html")
@Dependencies(GtBox, GtButton)    
class ProfileInfos extends PolymerElement {
	@Property public user: number;
	
	@Property({ computed: "user" })
	private get editable(): boolean {
		return this.app.user.id == this.user;
	}
}

@Element("profile-chars-card", "/assets/views/profile.html")
@Dependencies(GtBox, GtButton, BnetThumb)    
class ProfileCharsCard extends PolymerElement {
	@Inject
	private roster: Roster;
	
	@Property public id: number;
	@Property public editable: boolean
	@Property public char: Char;
	
	private update_pending = false;
	
	@Property({ computed: "char.last_update update_pending" })
	public get updatable(): boolean {
		if (this.update_pending) return false;
		let dt = Date.now() - this.char.last_update;
		return dt > 1000 * 60 * 15;
	}
	
	@Listener("btn-disable.click")
	public Disable() { this.roster.disableChar(this.id); }
	
	@Listener("btn-enable.click")
	public Enable() { this.roster.enableChar(this.id); }
	
	@Listener("btn-main.click")
	public Promote() { this.roster.promoteChar(this.id); }
	
	@Listener("btn-remove.click")
	public Remove() { this.roster.removeChar(this.id); }
	
	@Listener("btn-update.click")
	public async Update() {
		this.update_pending = true;
		try {
			await this.roster.updateChar(this.id);
		} finally {
			this.update_pending = false;
		}
	}
}

@Element("profile-add-char", "/assets/views/profile.html")
@Dependencies(GtBox, GtForm, GtInput)    
export class ProfileAddChar extends PolymerElement {
	@Inject
	private profile: Profile;
	
	@Property public owner: number;
	
	private server: string;
	private name: string;
	private role: string;
	
	private char: Char;
	private load_in_progress = false;
	
	@Property({ computed: "server name load_in_progress" })
	private get canLoad(): boolean {
		return !!this.server && !!this.name && !this.load_in_progress;
	}
	
	private roleClicked(e: MouseEvent) {
		let img = <HTMLImageElement> e.target;
		this.role = img.getAttribute("role");
	}
	
	private async load() {
		if (!this.canLoad) return;
		this.load_in_progress = true;
		
		let input: GtInput = this.$.name;
		input.error = null;
		
		try {
			let available = await this.profile.checkAvailability(this.server, this.name);
			if (!available) {
				throw new Error("This character is already registered");
			}
			
			let char = this.char = await this.profile.fetchChar(this.server, this.name);
			
			let img = document.createElement("img");
			img.src = "http://eu.battle.net/static-render/eu/" + char.thumbnail.replace("avatar", "profilemain");
			this.$.background.appendChild(img);
			img.onload = () => img.classList.add("loaded");
			
			this.role = char.role;
			
			console.log(this.char);
		} catch (e) {
			input.error = e.message;
			input.value = "";
			input.focus();
		} finally {
			this.load_in_progress = false;
		}
	}
	
	public show() {
		// Reset fields values
		this.name = "";
		this.server = "";
		this.role = "UNKNOWN";
		
		// Focus the server field
		this.$.server.focus();
		
		// Remove old backgrounds from past loading
		let background: HTMLDivElement = this.$.background;
		let imgs = background.querySelectorAll<HTMLImageElement>("img:not([default])");
		for (let i = 0; i < imgs.length; i++) {
			imgs[i].remove();
		}
		// Actually show the dialog
		this.$.dialog.show();
	}
}

@Element("profile-chars", "/assets/views/profile.html")
@Dependencies(GtBox, GtButton, GtDialog, ProfileCharsCard, ProfileAddChar)    
class ProfileChars extends PolymerElement {
	@Inject
	@On({
		"user-updated": "UserUpdated",
		"char-updated": "CharUpdated",
		"char-deleted": "CharUpdated"})
	private roster: Roster;
	
	@Property({ observer: "update" })
	public user: number;
	
	@Property public chars: number[];
	
	private UserUpdated(user: User) {
		if (user.id == this.user) this.update();
	}
	
	private CharUpdated(char: Char) {
		if (char.owner == this.user) this.update();
	}
	
	@throttle private update() {
		if (!this.user) return;
		this.chars = this.roster.getUserCharacters(this.user);
	}
	
	@Property({ computed: "user" })
	private get editable(): boolean {
		return this.app.user.id == this.user;
	}
	
	private AddChar() {
		this.$.addchar.show();
	}
}

@View("profile", "gt-profile", "/profile(/:user)?")
@Dependencies(ProfileUser, ProfileInfos, ProfileChars)
export class GtProfile extends PolymerElement {
	@Property
	public user: number = this.app.user.id;
}
