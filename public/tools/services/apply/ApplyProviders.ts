import {Provider, Property, Inject, On} from "../../polymer/Annotations";
import {PolymerElement} from "../../polymer/PolymerElement";
import {ApplyService, Apply} from "./ApplyService";
import {join} from "../../utils/Async";

/**
 * Translate numeric apply stages to textual names
 */
@Provider("apply-stage-name")
class StageNameProvider extends PolymerElement {
	@Property({observer: "update"})
	public stage: number;

	@Property({notify: true})
	public name: string;

	public update() {
		this.name = this.map(this.stage);
	}

	private map(stage: number) {
		switch (stage) {
			case 0:
				return "Pending";
			case 1:
				return "Review";
			case 2:
				return "Trial";
			case 3:
				return "Accepted";
			case 4:
				return "Refused";
			case 5:
				return "Archived";
			case 6:
				return "Spam";
			default:
				return "Unknown";
		}
	}
}

/**
 * Application data fetcher
 */
@Provider("apply-data")
class DataProvider extends PolymerElement {
	@Inject
	@On({"apply-updated": "ApplyUpdated"})
	private service: ApplyService;

	@Property({observer: "update"})
	public apply: number;

	@Property({notify: true})
	public data: Apply;

	@join
	public async update() {
		if (await microtask, !this.apply) return;
		this.data = await this.service.applyData(this.apply);
	}

	private ApplyUpdated(apply: Apply) {
		if (apply.id == this.apply) this.update();
	}
}

/**
 * Application unread status
 */
@Provider("apply-unread")
class UnreadProvider extends PolymerElement {
	@Inject
	@On({"unread-updated": "UnreadUpdated"})
	private service: ApplyService;

	@Property({observer: "update"})
	public apply: number;

	@Property({notify: true})
	public unread: boolean;

	@join
	public async update() {
		if (await microtask, !this.apply) return;
		this.unread = this.service.unreadState(this.apply);
	}

	private UnreadUpdated(apply: number) {
		if (this.apply == apply) this.update();
	}
}
