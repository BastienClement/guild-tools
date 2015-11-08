import { Element, Property, Listener, Dependencies, Inject, On, PolymerElement } from "elements/polymer";
import { View, Tab, TabsGenerator } from "elements/app";
import { GtButton, GtForm, GtInput } from "elements/widgets";
import { GtBox } from "elements/box";
import { Streams, ActiveStream } from "services/streams";
import { throttled } from "utils/async";
import "services/roster";

const StreamsTabs: TabsGenerator = (view, path, user) => [
	{ title: "Live Streams", link: "/streams", active: view == "views/streams/GtStreams" },
	{ title: "Settings", link: "/streams/settings", active: view == "views/streams/GtStreamsSettings" },
	{ title: "Whitelist", link: "/streams/whitelist", active: view == "views/streams/GtStreamsWhitelist", hidden: !user.promoted }
];

///////////////////////////////////////////////////////////////////////////////
// <gt-streams-item>

@Element("gt-streams-item", "/assets/views/streams.html")
@Dependencies(GtBox)
export class GtStreamsItem extends PolymerElement {
	@Property public stream: ActiveStream;
}

///////////////////////////////////////////////////////////////////////////////
// <gt-streams>

@View("streams", StreamsTabs)
@Element("gt-streams", "/assets/views/streams.html")
@Dependencies(GtBox, GtStreamsItem)
export class GtStreams extends PolymerElement {
	@Inject private service: Streams;
}

///////////////////////////////////////////////////////////////////////////////
// <gt-streams-settings>

@View("streams", StreamsTabs)
@Element("gt-streams-settings", "/assets/views/streams.html")
@Dependencies()
export class GtStreamsSettings extends PolymerElement {
	@Inject private service: Streams;
}

///////////////////////////////////////////////////////////////////////////////
// <gt-streams-whitelist>

@View("streams", StreamsTabs)
@Element("gt-streams-whitelist", "/assets/views/streams.html")
@Dependencies()
export class GtStreamsWhitelist extends PolymerElement {
	@Inject private service: Streams;
}

