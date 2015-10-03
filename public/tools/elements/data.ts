import { Element, Property, Dependencies, PolymerElement, Inject, On } from "elements/polymer";
import { Roster, User, Char } from "services/roster"

abstract class Provider extends PolymerElement {
	constructor() {
		super();
		this.node.node.style.display = "none";
	}
}

@Element("data-main")
export class DataMain extends Provider {
	@Inject
	@On({ "char-updated": "CharUpdated" })
	private roster: Roster;

	@Property({ type: Number, observer: "update" })
	public user: number;
	
	@Property({ type: Object, notify: true })
	public main: Char;

	public update() {
		this.fire("updated");
		this.main = this.roster.getMainCharacter(this.user);
	}
	
	private CharUpdated(char: Char) {
		if (char.owner == this.user && (char.main || char.id == this.main.id)) {
			this.update();
		}
	}
}

@Element("data-char")
export class DataChar extends Provider {
	@Inject
	@On({ "char-updated": "CharUpdated" })
	private roster: Roster;

	@Property({ type: Number, observer: "update" })
	public id: number;
	
	@Property({ type: Object, notify: true })
	public char: Char;

	public update() {
		this.fire("updated");
		this.char = this.roster.getCharacter(this.id);
	}
	
	private CharUpdated(char: Char) {
		if (char.id == this.char.id) {
			this.update();
		}
	}
}

@Element("data-user")
export class DataUser extends Provider {
	@Inject
	@On({ "user-updated": "UserUpdated" })
	private roster: Roster;

	@Property({ type: Number, observer: "update" })
	public id: number;
	
	@Property({ type: Object, notify: true })
	public user: User;

	public update() {
		this.fire("updated");
		this.user = this.roster.getUser(this.id);
	}
	
	private UserUpdated(user: User) {
		if (user.id == this.id) this.user = user;
	}
}

@Element("data-class")
export class DataClass extends Provider {
	@Property({ type: Number, observer: "update" })
	public id: number;
	
	@Property({ type: String, notify: true })
	public name: string;

	private class_name(id: number) {
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
		this.fire("updated");
		this.name = this.class_name(this.id);
	}
}

@Element("data-race")
export class DataRace extends Provider {
	@Property({ type: Number, observer: "update" })
	public id: number;
	
	@Property({ type: String, notify: true })
	public name: string;

	private race_name(id: number) {
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
		this.fire("updated");
		this.name = this.race_name(this.id);
	}
}

@Element("data-rank")
export class DataRank extends Provider {
	@Property({ type: Number, observer: "update" })
	public id: number;
	
	@Property({ type: String, notify: true })
	public name: string;

	private rank_name(id: number) {
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
		this.fire("updated");
		this.name = this.rank_name(this.id);
	}
}
