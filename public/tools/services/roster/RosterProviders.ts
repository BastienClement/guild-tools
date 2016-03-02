import {Provider, Inject, On, Property} from "../../polymer/Annotations";
import {PolymerElement} from "../../polymer/PolymerElement";
import {join} from "../../utils/Async";
import {RosterService, Char, User} from "./RosterService";

/**
 * Data provider for the main character of a user
 */
@Provider("roster-main")
export class MainProvider extends PolymerElement {
	@Inject
	@On({ "char-updated": "CharUpdated" })
	private roster: RosterService;

	@Property({ observer: "update" })
	public user: number;

	@Property({ notify: true })
	public main: Char;

	@join public async update() {
		await microtask;
		if (!this.user) return;
		this.main = this.roster.getMainCharacter(this.user);
	}

	private CharUpdated(char: Char) {
		if (char.owner == this.user && (char.main || char.id == this.main.id)) {
			this.update();
		}
	}
}

/**
 * Data provider for the list of a user's characters
 */
@Provider("roster-chars")
export class CharsProvider extends PolymerElement {
	@Inject
	@On({
		"char-updated": "CharUpdated",
		"char-deleted": "CharUpdated"
	})
	private roster: RosterService;

	@Property({ observer: "update" })
	public user: number;

	@Property({ reflect: true })
	public inactive: boolean;

	@Property({ notify: true })
	public chars: number[];

	@join public async update() {
		await microtask;
		if (!this.user) return;
		this.chars = this.roster.getUserCharacters(this.user, this.inactive);
	}

	private CharUpdated(char: Char) {
		if (char.owner == this.user) {
			this.update();
		}
	}
}

/**
 * Data provider for character details
 */
@Provider("roster-char")
export class CharProvider extends PolymerElement {
	@Inject
	@On({ "char-updated": "CharUpdated" })
	private roster: RosterService;

	@Property({ observer: "update" })
	public id: number;

	@Property({ notify: true })
	public char: Char;

	@join public async update() {
		await microtask;
		if (!this.id) return;
		this.char = this.roster.getCharacter(this.id);
	}

	private CharUpdated(char: Char) {
		if (char.id == this.char.id) {
			this.update();
		}
	}
}

/**
 * Provider for user details
 */
@Provider("roster-user")
export class UserProvider extends PolymerElement {
	@Inject
	@On({ "user-updated": "UserUpdated" })
	private roster: RosterService;

	@Property({ observer: "update" })
	public id: number;

	@Property({ observer: "update"})
	public current: boolean;

	@Property({ notify: true })
	public user: User;

	@join public async update() {
		await microtask;
		if (!this.current && !this.id) return;
		this.user = this.current ? this.app.user : this.roster.getUser(this.id);
	}

	private UserUpdated(user: User) {
		if (user.id == this.id) this.user = user;
	}
}

/**
 * Translate class id to names
 */
@Provider("roster-class")
export class ClassProvider extends PolymerElement {
	@Property({ observer: "update" })
	public id: number;

	@Property({ notify: true })
	public name: string;

	public static humanize(id: number): string {
		switch (id) {
			case 1: return "Warrior";
			case 2: return "Paladin";
			case 3: return "Hunter";
			case 4: return "Rogue";
			case 5: return "Priest";
			case 6: return "Death Knight";
			case 7: return "Shaman";
			case 8: return "Mage";
			case 9: return "Warlock";
			case 10: return "Monk";
			case 11: return "Druid";
			default: return "Unknown";
		}
	}

	public update() {
		this.name = ClassProvider.humanize(this.id);
	}
}

/**
 * Translate race id to names
 */
@Provider("roster-race")
export class RaceProvider extends PolymerElement {
	@Property({ observer: "update" })
	public id: number;

	@Property({ notify: true })
	public name: string;

	public static humanize(id: number): string {
		switch (id) {
			case 1: return "Human";
			case 2: return "Orc";
			case 3: return "Dwarf";
			case 4: return "Night Elf";
			case 5: return "Undead";
			case 6: return "Tauren";
			case 7: return "Gnome";
			case 8: return "Troll";
			case 9: return "Goblin";
			case 10: return "Blood Elf";
			case 11: return "Draenei";
			case 22: return "Worgen";
			case 24: case 25: case 26:
			return "Pandaren";
			default: return "Unknown";
		}
	}

	public update() {
		this.name = RaceProvider.humanize(this.id);
	}
}

/**
 * Translate rank id to names
 */
@Provider("roster-rank")
export class RankProvider extends PolymerElement {
	@Property({ observer: "update" })
	public id: number;

	@Property({ notify: true })
	public name: string;

	public static humanize(id: number): string {
		switch (id) {
			case 10: return "Guest";
			case 12: return "Casual";
			case 8: return "Apply";
			case 9: return "Member";
			case 11: return "Officer";
			case 13: return "Veteran";
			default: return "Unknown";
		}
	}

	public update() {
		this.name = RankProvider.humanize(this.id);
	}
}
