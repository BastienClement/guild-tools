import {Component} from "../../utils/DI";
import {Service} from "../../utils/Service";
import {Server} from "../../client/server/Server";
import {ServiceChannel} from "../../client/server/ServiceChannel";
import {synchronized} from "../../utils/Async";
import {compile_filters, Filter} from "./RosterFilters";

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

export interface UserRecord {
	infos: User;
	chars: Map<number, Char>;
	fake: boolean;
}

export type QueryResult = {
	user: User,
	main: Char,
	chars: Char[]
};

/**
 * Roster service
 */
@Component
export class RosterService extends Service {
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

	@synchronized
	private async preload() {
		if (this.preloaded) return false;
		let data = await this.channel.request<[User, Char[]][]>("preload-roster");
		for (let [user, chars] of data) {
			this.UserUpdated(user, chars);
		}
		this.preloaded = true;
		this.emit("preload-done");
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
		return this.channel.request<boolean>("change-role", {char, role});
	}

	// --- Query -------------------------------------------------------------

	private compileQuery(query: string): [Filter, string] {
		let filter_defs: [string, string][] = [];
		let search_query = query.replace(/\s*([a-z]+):([^\s]+)\s*/g, function (_, filter, arg) {
			filter_defs.push([filter, arg]);
			return "";
		});

		return [compile_filters(filter_defs), search_query];
	}

	public executeQueryChars(query: string): Char[] {
		let [filters, search] = this.compileQuery(query);
		let results: Char[] = [];

		this.users.forEach(user => {
			filters(user).forEach(c => results.push(c));
		});

		results.sort((a, b) => a.name.localeCompare(b.name));

		return results;
	}

	public executeQuery(query: string): QueryResult[] {
		let [filters, search] = this.compileQuery(query);
		let results: QueryResult[] = [];

		this.users.forEach(user => {
			let matching_chars = filters(user);
			if (matching_chars.length > 0) {
				results.push({
					user: user.infos,
					main: this.getMainCharacter(user.infos.id),
					chars: matching_chars
				});
			}
		});

		results.sort((a, b) => a.user.name.localeCompare(b.user.name));

		return results;
	}
}

