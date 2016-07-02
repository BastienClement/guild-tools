import {Element, Inject, On, Property} from "../../polymer/Annotations";
import {GtBox} from "../widgets/GtBox";
import {GtButton} from "../widgets/GtButton";
import {GtDialog} from "../widgets/GtDialog";
import {ProfileAddChar} from "./ProfileAddChar";
import {PolymerElement} from "../../polymer/PolymerElement";
import {RosterService, Char, User} from "../../services/roster/RosterService";
import {throttled} from "../../utils/Async";
import {ProfileCharsCard} from "./ProfileCharsCard";

///////////////////////////////////////////////////////////////////////////////
// <profile-chars>

@Element({
	selector: "profile-chars",
	template: "/assets/views/profile.html",
	dependencies: [GtBox, GtButton, GtDialog, ProfileCharsCard, ProfileAddChar]
})
export class ProfileChars extends PolymerElement {
	@Inject
	@On({
		"user-updated": "UserUpdated",
		"char-updated": "CharUpdated",
		"char-deleted": "CharUpdated"
	})
	private roster: RosterService;

	@Property({observer: "update"})
	public user: number;

	@Property({computed: "user"})
	private get me(): boolean {
		return this.user == this.app.user.id;
	}

	@Property({computed: "user"})
	private get editable(): boolean {
		return this.app.user.id == this.user || this.app.user.promoted;
	}

	@Property
	public chars: number[];

	private UserUpdated(user: User) {
		if (user.id == this.user) this.update();
	}

	private CharUpdated(char: Char) {
		if (char.owner == this.user) this.update();
	}

	@throttled
	private update() {
		if (!this.user) return;
		this.chars = this.roster.getUserCharacters(this.user, true);
	}

	private AddChar() {
		this.$.addchar.show();
	}
}
