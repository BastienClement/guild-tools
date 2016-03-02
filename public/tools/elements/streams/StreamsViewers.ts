import {Element, Property, Inject, On} from "../../polymer/Annotations";
import {GtBox} from "../widgets/GtBox";
import {PolymerElement} from "../../polymer/PolymerElement";
import {StreamsService, ActiveStream} from "../../services/stream/StreamService";

///////////////////////////////////////////////////////////////////////////////
// <gt-streams-viewers-item>

@Element({
	selector: "streams-viewers-item",
	template: "/assets/views/streams.html",
	dependencies: [GtBox]
})
export class StreamsViewersItem extends PolymerElement {
	@Property public user: number;
}

///////////////////////////////////////////////////////////////////////////////
// <gt-streams-viewers>

@Element({
	selector: "streams-viewers",
	template: "/assets/views/streams.html",
	dependencies: [StreamsViewersItem, GtBox]
})
export class StreamsViewers extends PolymerElement {
	@Inject
	@On({
		"list-update": "Update",
		"notify": "Update"
	})
	private service: StreamsService;

	// The currently selected stream
	@Property({ observer: "Update" })
	public stream: ActiveStream;

	// List of viewers for this stream
	protected viewers: number[];

	// Update the viewers list
	protected Update() {
		if (!this.stream) return;
		this.viewers = this.service.getStreamViewers(this.stream.user);
	}
}
