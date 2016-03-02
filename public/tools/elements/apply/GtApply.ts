import {TabsGenerator, View} from "../../client/router/View";
import {GtBox, GtAlert} from "../widgets/GtBox";
import {RosterService} from "../../services/roster/RosterService";
import {PolymerElement} from "../../polymer/PolymerElement";
import {Inject, On, Property, Element} from "../../polymer/Annotations";
import {GtDialog} from "../widgets/GtDialog";
import {GtButton} from "../widgets/GtButton";
import {ApplyService} from "../../services/apply/ApplyService";
import {ApplyDetails} from "./ApplyDetails";
import {GtTimeago} from "../misc/GtTimeago";
import {BnetThumb} from "../misc/BnetThumb";

const ApplyTabs: TabsGenerator = (view, path, user) => [
	{title: "Applys", link: "/apply", active: view == GtApply},
	{title: "Archives", link: "/apply/archives", active: view == null}
];

@Element({
	selector: "apply-list-item",
	template: "/assets/views/apply.html",
	dependencies: [GtBox, BnetThumb, GtTimeago]
})
export class ApplyListItem extends PolymerElement {
	@Property public apply: number;
}

@View("apply", ApplyTabs, true)
@Element({
	selector: "gt-apply",
	template: "/assets/views/apply.html",
	dependencies: [GtBox, GtAlert, GtButton, GtDialog, ApplyListItem, ApplyDetails]
})
export class GtApply extends PolymerElement {
	@Inject
	@On({"apply-updated": "ApplyUpdated"})
	private service: ApplyService;

	private applys: number[];

	@Property({observer: "ApplySelected"})
	private selected: number = null;

	private async init() {
		let selected = this.selected;
		this.selected = void 0;
		this.applys = await this.service.openApplysList();
		this.selected = selected;
	}

	private async ApplyUpdated() {
		this.applys = await this.service.openApplysList();
	}

	private ApplySelected() {
		if (!this.applys) return;
		if (!this.applys.some(a => a === this.selected)) {
			this.selected = void 0;
		}
	}

	private SelectApply(ev: PolymerModelEvent<number>) {
		this.app.router.goto(`/apply/${ev.model.item}`);
	}
}
