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
	private char: string;
	private load_in_progress = false;
	
	@Property({ computed: "server char load_in_progress" })
	private get canLoad(): boolean {
		return !!this.server && !!this.char && !this.load_in_progress;
	}
	
	public show() {
		this.$.dialog.show();
		this.$.char.focus();
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
