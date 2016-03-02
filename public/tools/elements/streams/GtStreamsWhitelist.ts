import {Element, Inject} from "../../polymer/Annotations";
import {StreamsTabs} from "./GtStreams";
import {View} from "../../client/router/View";
import {PolymerElement} from "../../polymer/PolymerElement";
import {StreamsService} from "../../services/stream/StreamService";

///////////////////////////////////////////////////////////////////////////////
// <gt-streams-whitelist>

@View("streams", StreamsTabs)
@Element({
	selector: "gt-streams-whitelist",
	template: "/assets/views/streams.html"
})
export class GtStreamsWhitelist extends PolymerElement {
	@Inject
	private service: StreamsService;
}

