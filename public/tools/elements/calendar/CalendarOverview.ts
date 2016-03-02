import {Element} from "../../polymer/Annotations";
import {GtAlert, GtBox} from "../widgets/GtBox";
import {PolymerElement} from "../../polymer/PolymerElement";

@Element({
	selector: "calendar-overview",
	template: "/assets/views/calendar.html",
	dependencies: [GtBox, GtAlert]
})
export class CalendarOverview extends PolymerElement {
}
