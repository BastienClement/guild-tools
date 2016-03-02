import {Element, Inject, Property} from "../../polymer/Annotations";
import {StreamsTabs} from "./GtStreams";
import {View} from "../../client/router/View";
import {GtLabel} from "../widgets/GtLabel";
import {GtCheckbox} from "../widgets/GtCheckbox";
import {GtButton} from "../widgets/GtButton";
import {GtBox} from "../widgets/GtBox";
import {PolymerElement} from "../../polymer/PolymerElement";
import {StreamsService} from "../../services/stream/StreamService";
import {join} from "../../utils/Async";

///////////////////////////////////////////////////////////////////////////////
// <gt-streams-settings>

@View("streams", StreamsTabs)
@Element({
	selector: "gt-streams-settings",
	template: "/assets/views/streams.html",
	dependencies: [GtBox, GtButton, GtCheckbox, GtLabel]
})
export class GtStreamsSettings extends PolymerElement {
	@Inject private service: StreamsService;

	public token: string;
	public key: string;
	public visibility: string;

	private visibility_check: boolean = false;

	@join private async updateToken() {
		let data = await this.service.ownTokenVisibility();
		if (data) {
			let [token, key, progress] = data;
			this.token = token;
			this.key = key;
			this.visibility = progress ? "limited" : "open";
		} else {
			this.token = null;
			this.key = null;
			this.visibility = "open";
		}
	}

	@Property({ computed: "token" })
	private get hasToken(): boolean {
		return this.token !== null;
	}

	private init() {
		this.updateToken();
	}

	private generating = false;
	private async Generate() {
		if (this.generating) return;
		this.generating = true;
		try {
			await this.service.createToken();
			this.updateToken();
		} finally {
			this.generating = false;
		}
	}

	private async UpdateVisibility() {
		this.visibility_check = false;
		await this.service.changeOwnVisibility(this.visibility == "limited");
		this.visibility_check = true;
		this.debounce("hide_visibility_check", () => this.visibility_check = false, 3000);
	}
}
