import { Element, Dependencies, PolymerElement, Inject, Property, PolymerModelEvent } from "elements/polymer";
import { View } from "elements/app";
import { GtBox } from "elements/box";
import { GtButton, GtCheckbox, GtLabel } from "elements/widgets";
import { GtDialog } from "elements/dialog";
import { Server } from "../client/Server";
import { User } from "services/roster";

type SettingsTab = [string, string, string];

@View("settings", () => [{ title: "Settings", link: "/settings", active: true }])
@Element("gt-settings", "/assets/views/settings.html")
@Dependencies(GtBox, GtButton, GtDialog, GtCheckbox, GtLabel)
export class GtSettings extends PolymerElement {
	private tabs: SettingsTab[] = [
		//["General", "settings", "general"],
		//["Notifications", "notifications", "notifications"],
		//["Game", "folder", "game"],
		["Advanced", "build", "advanced"]
	];

	private tab = "advanced";

	private SelectTab(ev: PolymerModelEvent<SettingsTab>) {
		let tab = ev.model.item;
		this.tab = tab[2];
	}

	// Advanced
	private faster_loading = localStorage.getItem("loading.fast") === "1";
	private verbose_gtp3 = localStorage.getItem("socket.verbose") === "1";
	private sourcemap_fix = localStorage.getItem("traceur.sourcemap.fix") === "1";
	private streams_zerolatency = localStorage.getItem("streams.zerolatency") === "1";

	private ToggleLocalStorage(ev: Event) {
		let target = <GtCheckbox & HTMLElement> ev.target;
		let key = target.getAttribute("key");
		localStorage.setItem(key, target.checked ? "1" : "0");
	}
}
