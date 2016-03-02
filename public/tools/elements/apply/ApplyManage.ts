import {Element, Inject, Property, Listener} from "../../polymer/Annotations";
import {GtCheckbox} from "../widgets/GtCheckbox";
import {GtButton} from "../widgets/GtButton";
import {GtProgressCircular} from "../widgets/GtProgressCircular";
import {GtBox} from "../widgets/GtBox";
import {PolymerElement} from "../../polymer/PolymerElement";
import {ApplyService, Apply} from "../../services/apply/ApplyService";
import {ApplyStageNameProvider} from "../../services/apply/ApplyProviders";

@Element({
	selector: "apply-manage",
	template: "/assets/views/apply.html",
	dependencies: [GtBox, GtProgressCircular, GtButton, GtCheckbox, ApplyStageNameProvider]
})
export class ApplyManage extends PolymerElement {
	@Inject
	private service: ApplyService;

	@Property
	public data: Apply;

	@Property
	public stage: number;

	private locked: boolean = false;

	@Listener("save.click")
	private async OnSave() {
		if (this.locked) return;
		this.locked = true;

		try {
			await this.service.changeApplicationStage(this.data.id, this.stage);
			this.fire("close-manage-dialog");
		} catch (e) {
		} finally {
			this.locked = false;
		}
	}

	@Listener("close.click")
	private async OnClose() {
		this.fire("close-manage-dialog");
	}
}
