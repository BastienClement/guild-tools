import {UserRecord, Char} from "./RosterService";

/**
 * Filtering types
 */
export type FilterDefinition = [string, string];
export type Filter = (record: UserRecord) => Char[];
export type FilterFactory = (defs: FilterDefinition[]) => Filter;

export type Predicate<T> = (subject: T) => boolean;
export type UserPredicate = Predicate<UserRecord>;
export type CharPredicate = Predicate<Char>;

const EMPTY_ARRAY: any[] = [];

type NameProvider<T> = { humanize(arg: any): string };
const providers: { [name: string]: NameProvider<any> } = {};

for (let name of ["RosterRankProvider", "RosterClassProvider", "RosterRaceProvider"]) {
	(async () => {
		providers[name] = <any> (await Promise.require("services/roster/RosterProviders", name));
	})();
}

export const compile_filters: FilterFactory = (defs: FilterDefinition[]) => {
	let user_filters: UserPredicate[] = [];
	let char_filters: CharPredicate[] = [];

	// Constructs a filter accepting multiple comma-separated alternatives
	type NameProvider<T> = { humanize(arg: T): string };
	const provider_filter = <T, U>(arg: string, provider: string, category: Predicate<U>[], extractor: (s: U) => T) => {
		// Transform alternatives string to array of predicate functions
		let filters = arg.split(",").map((option: string) => {
			return (arg: T) => providers[provider].humanize(arg).toLowerCase().replace(" ", "") == option;
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

	// Constructs a filter operating on boolean flags
	const boolean_filters = <T>(arg: string, category: Predicate<T>[], extractor: (subject: T) => boolean) => {
		category.push((subject: T) => {
			let predicate = extractor(subject);
			return arg == "1" ? predicate : !predicate;
		});
	};

	// Constructs a filter based on equality
	const equals_filters = <T>(arg: string, category: Predicate<T>[], extractor: (subject: T) => string) => {
		// Transform alternatives string to array of predicate functions
		let filters = arg.split(",").map((option: string) => {
			option = option.toLowerCase()
			return (arg: string) => arg.toLowerCase() == option;
		});

		// Register the overall filter
		// The options acts as a logical OR combinator
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
				provider_filter(arg, "RosterRankProvider", user_filters, (record: UserRecord) => record.infos.group);
				break;
			case "class":
				provider_filter(arg, "RosterClassProvider", char_filters, (char: Char) => char.class);
				break;
			case "race":
				provider_filter(arg, "RosterRaceProvider", char_filters, (char: Char) => char.race);
				break;
			case "level":
				interval_filter(arg, char_filters, (char: Char) => char.level);
				break;
			case "ilvl":
				interval_filter(arg, char_filters, (char: Char) => char.ilvl);
				break;
			case "role":
				equals_filters(arg, char_filters, (char: Char) => {
					let role = char.role;
					if (role == "HEALING") role = "HEALER";
					return role;
				});
				break;
			case "main":
				boolean_filters(arg, char_filters, (char: Char) => char.main);
				break;
			case "active":
				boolean_filters(arg, char_filters, (char: Char) => char.active);
				break;
			case "valid":
				boolean_filters(arg, char_filters, (char: Char) => !char.invalid);
				break;
			case "guilded":
				// TODO: I dont have guild information in database !?
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
