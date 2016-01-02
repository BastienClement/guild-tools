import { Component } from "utils/di";
import { Service } from "utils/service";
import { join, synchronized } from "utils/async";
import { Server, ServiceChannel } from "client/server";
import { PolymerElement, Provider, Inject, Property, On } from "elements/polymer";

export interface User {
	id: number;
	name: string;
	group: number;
	color: string;
	officer: boolean;
	developer: boolean;
	promoted: boolean;
	member: boolean;
	roster: boolean;
	fs: boolean;
}

export interface Char {
	id: number;
	name: string;
	server: string;
	owner: number;
	main: boolean;
	active: boolean;
	"class": number;
	race: number;
	gender: number;
	level: number;
	achievement: number;
	thumbnail: string;
	ilvl: number;
	role: string;
	invalid: boolean;
	last_update: number;
}

interface UserRecord {
	infos: User;
	chars: Map<number, Char>;
	fake: boolean;
}

/**
 * Filtering types
 */
type FilterDefinition = [string, string];
type Filter = (record: UserRecord) => Char[];
type FilterFactory = (defs: FilterDefinition[]) => Filter;

type Predicate<T> = (subject: T) => boolean;
type UserPredicate = Predicate<UserRecord>;
type CharPredicate = Predicate<Char>;

const EMPTY_ARRAY: any[] = [];

const compile_filters: FilterFactory = (defs: FilterDefinition[]) => {
	let user_filters: UserPredicate[] = [];
	let char_filters: CharPredicate[] = [];

	// Constructs a filter accepting multiple comma-separated alternatives
	type NameProvider<T> = { name(arg: T): string };
	const provider_filter = <T, U>(arg: string, provider: NameProvider<T>, category: Predicate<U>[], extractor: (s: U) => T) => {
		// Transform alternatives string to array of predicate functions
		let filters = arg.split(",").map((option: string) => {
			return (arg: T) =>  provider.name(arg).toLowerCase().replace(" ", "") == option;
		});

		// Register the overall filter
		// The options acts as a logical OR combinator
		category.push((subject: U) => {
			let effective_subject = extractor(subject);
			return filters.some(p => p(effective_subject));
		});
	};

	// Constructs a filter operating on number intervals
	const interval_filter = <T>(arg: string, category: Predicate<T>[], extractor: (subject: T) => number) => {
		let filters = arg.split(",").map((option: string) => {
			let dash = option.indexOf("-");
			if (dash === -1) {
				let value = Number.parseFloat(option);
				return (arg: number) => Math.abs(arg - value) < Number.EPSILON;
			} else if (dash == 0) {
				let value = Number.parseFloat(option.slice(1));
				return (arg: number) => arg <= value;
			} else if (dash == option.length - 1) {
				let value = Number.parseFloat(option.slice(0, -1));
				return (arg: number) => arg >= value;
			} else {
				let [lower, upper] = option.split("-").map(Number.parseFloat);
				return (arg: number) => arg >= lower && arg <= upper;
			}
		});

		category.push((subject: T) => {
			let effective_subject = extractor(subject);
			return filters.some(p => p(effective_subject));
		});
	};

	// Construct filters
	for (let def of defs) {
		let [filter, arg] = def;
		switch (filter) {
			case "rank":
				provider_filter(arg, RankProvider, user_filters, (record: UserRecord) => record.infos.group);
				break;
			case "class":
				provider_filter(arg, ClassProvider, char_filters, (char: Char) => char.class);
				break;
			case "race":
				provider_filter(arg, RaceProvider, char_filters, (char: Char) => char.race);
				break;
			case "level":
				interval_filter(arg, char_filters, (char: Char) => char.level);
				break;
			case "ilvl":
				interval_filter(arg, char_filters, (char: Char) => char.ilvl);
				break;
		}
	}

	// Apply filters
	return (record: UserRecord) => {
		if (!user_filters.every(f => f(record))) {
			return EMPTY_ARRAY;
		} else {
			return Array.from(record.chars.values()).filter(char => char_filters.every(f => f(char)));
		}
	};
};

/**
 * Roster service
 */
@Component
export class Roster extends Service {
	constructor(private server: Server) {
		super();
		this.preload();
	}

	// Roster channel
	private channel = this.server.openServiceChannel("roster");

	// Roster data
	private users = new Map<number, UserRecord>();
	private owners = new Map<number, number>();
	private chars_cache = new Map<number, number[]>();

	// Reflect the current state of the roster channel
	@ServiceChannel.ReflectState("channel")
	public available: boolean = false;

	// Preload roster users
	private preloaded = false;
	@synchronized private async preload() {
		if (this.preloaded) return false;
		let data = await this.channel.request<[User, Char[]][]>("preload-roster");
		for (let [user, chars] of data) {
			this.UserUpdated(user, chars);
		}
		this.preloaded = true;
		return true;
	}

	// Request an update for a user
	private async request(user: number) {
		if (!this.preloaded) await this.preload();
		if (this.users.has(user) && !this.users.get(user).fake) return;
		this.channel.send("request-user", user);
	}

	// --- Helpers ------------------------------------------------------------

	// Update the last modified date on a user record
	private touch(record: any, add: number = 60 * 15) {
		Reflect.defineMetadata("roster:stale", Date.now() / 1000 + add, record);
	}

	// Check if a given record is stale data
	private stale(record: any): boolean {
		let ts = Reflect.getMetadata<number>("roster:stale", record) || 0;
		return (Date.now() / 1000) > ts;
	}

	// Update an object from a more recent copy of it
	// Return true if any key changed between the two versions
	private update(a: any, b: any): boolean {
		let updated = false;
		for (let key in b) {
			if (a[key] !== b[key]) {
				updated = true;
				a[key] = b[key];
			}
		}
		return updated;
	}

	// Secure an object by copying properties to an empty object and freezing it
	private lock<T>(obj: T): T {
		return Object.freeze(Object.assign({}, obj));
	}

	// Construct a fake user
	private fakeUser(id: number): User {
		return {
			id: id,
			name: `User#${id}`,
			group: 0,
			color: "64B4FF",
			officer: false,
			developer: false,
			promoted: false,
			member: false,
			roster: false,
			fs: false
		};
	}

	// Construct a fake char
	private fakeChar(id: number, name: string, owner: number): Char {
		return {
			id: id,
			name: name,
			server: "",
			owner: owner,
			main: true,
			active: true,
			"class": 0,
			race: 10,
			gender: 0,
			level: 0,
			achievement: 0,
			thumbnail: "",
			ilvl: 0,
			role: "UNKNOWN",
			invalid: true,
			last_update: Date.now()
		};
	}

	// Return the local cached user, if none is available creates a fake one
	// Handle requesting update for stale data
	private getRecord(user: number) {
		let record = this.users.get(user);

		if (!record) {
			record = {
				infos: this.fakeUser(user),
				chars: new Map(),
				fake: true
			};
			this.users.set(user, record);
		}

		if (this.stale(record)) {
			this.request(user);
			this.touch(record, 30);
		}

		return record;
	}

	// --- Events -------------------------------------------------------------

	// Received a full user data (infos + chars)
	@ServiceChannel.Dispatch("channel", "user-data", true)
	private UserUpdated(user: User, chars: Char[]) {
		// Get previous user record
		let record = this.users.get(user.id);

		// No previous record available, so no-one asked for this user
		// We simply save received data, no need to emit any event
		if (!record) {
			// Convert char list to Map
			let char_map = new Map<number, Char>();
			for (let char of chars) {
				char_map.set(char.id, char);
				this.owners.set(char.id, user.id);
			}

			// Create record object
			record = {
				infos: user,
				chars: char_map,
				fake: false
			};

			// Save it
			this.touch(record);
			this.users.set(user.id, record);
			return;
		}

		// Update user informations
		if (this.update(record.infos, user)) {
			this.emit("user-updated", this.lock(user));
		}

		// Chars seen while updating, used to remove deleted chars
		let seen = new Set<number>();

		// Update all chars
		for (let char of chars) {
			let id = char.id;
			seen.add(id);

			let old = record.chars.get(id);
			if (!old) {
				record.chars.set(id, char);
				this.owners.set(id, char.owner);
			} else if (!this.update(old, char)) {
				continue;
			}

			this.emit("char-updated", this.lock(char));
		}

		// Prune removed characters
		for (let id of record.chars.keys()) {
			if (!seen.has(id)) {
				record.chars.delete(id);
				this.owners.delete(id);
				if (id != 0) this.emit("char-deleted", id);
			}
		}

		// Record the last update of the record
		this.chars_cache.delete(user.id);
		record.fake = false;
		this.touch(record);
	}

	// Char updated or added
	@ServiceChannel.Dispatch("channel", "char-updated")
	private CharUpdated(char: Char) {
		// Fetch the owner's record
		let record = this.users.get(char.owner);

		// There is no record available. For now we simply ignore the
		// received char and wait for the application to query the whole user
		if (!record) return;

		// Update
		let old = record.chars.get(char.id);
		if (!old) {
			record.chars.set(char.id, char);
			this.owners.set(char.id, char.owner);
		} else if (!this.update(old, char)) {
			return;
		}

		this.emit("char-updated", this.lock(char));

		this.chars_cache.delete(char.owner);
		this.touch(record);
	}

	// Char removed
	@ServiceChannel.Dispatch("channel", "char-deleted")
	private CharRemoved(char: Char) {
		let record = this.users.get(char.owner);
		if (!record) return;

		// Remove the char from local cache
		record.chars.delete(char.id);
		this.owners.delete(char.id);
		this.emit("char-deleted", char);

		this.chars_cache.delete(char.owner);
		this.touch(record);
	}

	// --- Public -------------------------------------------------------------

	public getUser(id: number) {
		let infos = this.getRecord(id).infos;
		return this.lock(infos);
	}

	public getUserCharacters(user: number, inactive?: boolean) {
		const filter_active = (id: number) => inactive || this.getCharacter(id).active;

		if (this.chars_cache.has(user)) {
			return this.chars_cache.get(user).filter(filter_active);
		}

		let chars = Array.from(this.getRecord(user).chars.keys());
		chars.sort((a_id, b_id) => {
			let a = this.getCharacter(a_id);
			let b = this.getCharacter(b_id);
			if (a.main != b.main) return a.main ? -1 : 1;
			else if (a.active != b.active) return a.active ? -1 : 1;
			else if (a.level != b.level) return b.level - a.level;
			else if (a.ilvl != b.ilvl) return b.ilvl - a.ilvl;
			return a.name.localeCompare(b.name);
		});

		this.chars_cache.set(user, chars);
		return chars.filter(filter_active);
	}

	public getMainCharacter(user: number) {
		let chars = this.getRecord(user).chars;
		for (let char of chars.values()) {
			if (char.main) return this.lock(char);
		}

		return this.fakeChar(-user, `User#${user}`, user);
	}

	public getCharacter(id: number) {
		if (id < 0) {
			return this.fakeChar(id, `User#${-id}`, -id);
		}

		let owner = this.owners.get(id);
		if (owner) {
			let record = this.getRecord(owner);
			let char = record.chars.get(id);
			if (char) {
				return this.lock(char);
			}
		}

		return this.fakeChar(id, `Char#${id}`, NaN);
	}

	// --- Update -------------------------------------------------------------

	public promoteChar(char: number) {
		return this.channel.request<[Char, Char]>("promote-char", char);
	}

	public disableChar(char: number) {
		return this.channel.request<Char>("disable-char", char);
	}

	public enableChar(char: number) {
		return this.channel.request<Char>("enable-char", char);
	}

	public removeChar(char: number) {
		return this.channel.request<Char>("remove-char", char);
	}

	public updateChar(char: number) {
		return this.channel.request<boolean>("update-char", char);
	}

	public changeRole(char: number, role: String) {
		return this.channel.request<boolean>("change-role", { char, role });
	}

	// --- Query -------------------------------------------------------------

	public executeQuery(query: string) {
		let filter_defs: [string, string][] = [];
		query = query.replace(/\s*([a-z]+):([^\s]+)\s*/g, function(_, filter, arg) {
			filter_defs.push([filter, arg]);
			return "";
		});

		let filters = compile_filters(filter_defs);

		return [filters, query];
	}
}

/**
 * Data provider for the main character of a user
 */
@Provider("roster-main")
class MainProvider extends PolymerElement {
	@Inject
	@On({ "char-updated": "CharUpdated" })
	private roster: Roster;

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
class CharsProvider extends PolymerElement {
	@Inject
	@On({
		"char-updated": "CharUpdated",
		"char-deleted": "CharUpdated"
	})
	private roster: Roster;

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
class CharProvider extends PolymerElement {
	@Inject
	@On({ "char-updated": "CharUpdated" })
	private roster: Roster;

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
class UserProvider extends PolymerElement {
	@Inject
	@On({ "user-updated": "UserUpdated" })
	private roster: Roster;

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
class ClassProvider extends PolymerElement {
	@Property({ observer: "update" })
	public id: number;

	@Property({ notify: true })
	public name: string;

	public static name(id: number): string {
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
		this.name = ClassProvider.name(this.id);
	}
}

/**
 * Translate race id to names
 */
@Provider("roster-race")
class RaceProvider extends PolymerElement {
	@Property({ observer: "update" })
	public id: number;

	@Property({ notify: true })
	public name: string;

	public static name(id: number): string {
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
		this.name = RaceProvider.name(this.id);
	}
}

/**
 * Translate rank id to names
 */
@Provider("roster-rank")
class RankProvider extends PolymerElement {
	@Property({ observer: "update" })
	public id: number;

	@Property({ notify: true })
	public name: string;

	public static name(id: number): string {
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
		this.name = RankProvider.name(this.id);
	}
}
