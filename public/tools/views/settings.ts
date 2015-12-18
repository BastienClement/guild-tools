import { Element, Dependencies, PolymerElement, Inject, Property, PolymerModelEvent } from "elements/polymer";
import { View } from "elements/app";
import { GtBox } from "elements/box";
import { GtButton, GtCheckbox, GtLabel } from "elements/widgets";
import { GtDialog } from "elements/dialog";
import { Server } from "client/server";
import { User } from "services/roster";

type SettingsTab = [string, string, string];

@View("settings", () => [{ title: "Settings", link: "/settings", active: true }])
@Element("gt-settings", "/assets/views/settings.html")
@Dependencies(GtBox, GtButton, GtDialog, GtCheckbox, GtLabel)
export class GtSettings extends PolymerElement {
	private tabs: SettingsTab[] = [
		["General", "settings", "general"],
		["Notifications", "notifications", "notifications"],
		["Game", "folder", "game"],
		["Advanced", "build", "advanced"]
	];

	private tab = "general";

	private SelectTab(ev: PolymerModelEvent<SettingsTab>) {
		let tab = ev.model.item;
		this.tab = tab[2];
	}
}
