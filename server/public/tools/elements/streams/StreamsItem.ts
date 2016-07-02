import {PolymerElement} from "../../polymer/PolymerElement";
import {Element, Property} from "../../polymer/Annotations";
import {GtBox} from "../widgets/GtBox";
import {ActiveStream} from "../../services/stream/StreamService";

///////////////////////////////////////////////////////////////////////////////
// <gt-streams-item>

@Element({
	selector: "streams-item",
	template: "/assets/views/streams.html",
	dependencies: [GtBox]
})
export class StreamsItem extends PolymerElement {
	@Property public stream: ActiveStream;
}
