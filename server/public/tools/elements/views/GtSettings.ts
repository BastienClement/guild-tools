import {PolymerElement} from "../../polymer/PolymerElement";
import {View} from "../../client/router/View";
import {Element} from "../../polymer/Annotations";
import {GtLabel} from "../widgets/GtLabel";
import {GtCheckbox} from "../widgets/GtCheckbox";
import {GtDialog} from "../widgets/GtDialog";
import {GtButton} from "../widgets/GtButton";
import {GtBox} from "../widgets/GtBox";

type SettingsTab = [string, string, string];

@View("settings", () => [{title: "Settings", link: "/settings", active: true}])
@Element({
	selector: "gt-settings",
	template: "/assets/views/settings.html",
	dependencies: [GtBox, GtButton, GtDialog, GtCheckbox, GtLabel]
})
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
