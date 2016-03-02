import {Element, Inject, On, Property} from "../../polymer/Annotations";
import {TabsGenerator, View} from "../../client/router/View";
import {StreamsPlayer} from "./StreamsPlayer";
import {GtAlert, GtBox} from "../widgets/GtBox";
import {StreamsViewers} from "./StreamsViewers";
import {StreamsItem} from "./StreamsItem";
import {PolymerElement} from "../../polymer/PolymerElement";
import {StreamsService, ActiveStream} from "../../services/stream/StreamService";
import {GtStreamsSettings} from "./GtStreamsSettings";
import {GtStreamsWhitelist} from "./GtStreamsWhitelist";

export const StreamsTabs: TabsGenerator = (view, path, user) => [
	{title: "Live Streams", link: "/streams", active: view == GtStreams},
	{title: "Publish", link: "/streams/settings", active: view == GtStreamsSettings},
	{title: "Whitelist", link: "/streams/whitelist", active: view == GtStreamsWhitelist, hidden: !user.promoted}
];

///////////////////////////////////////////////////////////////////////////////
// <gt-streams>

@View("streams", StreamsTabs)
@Element({
	selector: "gt-streams",
	template: "/assets/views/streams.html",
	dependencies: [GtBox, StreamsItem, StreamsViewers, GtAlert, StreamsPlayer]
})
export class GtStreams extends PolymerElement {
	@Inject
	@On({"offline": "StreamOffline"})
	private service: StreamsService;

	@Property
	public selected: ActiveStream = null;

	private StreamOffline(user: number) {
		if (this.selected && this.selected.user == user) {
			this.selected = null;
		}
	}

	private SelectStream(ev: PolymerModelEvent<ActiveStream>) {
		let stream = ev.model.item;
		this.selected = (this.selected && this.selected.user == stream.user) ? null : stream;
	}

	private StreamIsActive(user: number) {
		return this.selected && this.selected.user == user;
	}
}
