import {Constructor} from "../../utils/DI";
import {User} from "../../services/roster/RosterService";

// Tab
export interface Tab {
	title: string;
	link: string;
	active: boolean;
	hidden?: boolean;
}

// Function generating current tabs list
export type TabsGenerator = (view: Constructor<PolymerElement>, path: string, user: User) => Tab[];

// Metadata for views
export interface ViewMetadata {
	module: string;
	tabs: TabsGenerator;
	sticky: boolean;
}

// The @View annoation
export function View(module: string, tabs: TabsGenerator, sticky?: boolean) {
	return <T extends PolymerElement>(target: Constructor<T>) => {
		Reflect.defineMetadata("view:meta", { module, tabs, sticky }, target);
	};
}
