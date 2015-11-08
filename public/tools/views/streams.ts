import { Element, Property, Listener, Dependencies, Inject, On, PolymerElement } from "elements/polymer";
import { View, Tab, TabsGenerator } from "elements/app";
import { GtButton, GtForm, GtInput } from "elements/widgets";
import { GtBox } from "elements/box";
import { Streams } from "services/streams";
import { throttled } from "utils/async";

const StreamsTabs: TabsGenerator = (view, path, user) => [
	{ title: "Live Streams", link: "/streams", active: view == "views/streams/GtStreams" },
	{ title: "Streaming Key", link: "/streams/key", active: view == "views/streams/GtStreamsKey" },
	{ title: "Whitelist", link: "/streams/whitelist", active: view == "views/streams/GtStreamsWhitelist" }
];

///////////////////////////////////////////////////////////////////////////////
// <gt-streams>

@View("streams", StreamsTabs)
@Element("gt-streams", "/assets/views/streams.html")
@Dependencies()
export class GtStreams extends PolymerElement {
	@Inject private service: Streams;
}
