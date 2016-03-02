import {RouteDeclaration} from "./Router";
import {GtSettings} from "../../elements/views/GtSettings";
import {GtServerStatus} from "../../elements/views/GtServerStatus";
import {GtAbout} from "../../elements/views/GtAbout";
import {GtRoster} from "../../elements/roster/GtRoster";
import {GtStreamsWhitelist} from "../../elements/streams/GtStreamsWhitelist";
import {GtStreamsSettings} from "../../elements/streams/GtStreamsSettings";
import {GtStreams} from "../../elements/streams/GtStreams";
import {GtApplyRedirect} from "../../elements/apply/GtApplyRedirect";
import {GtApply} from "../../elements/apply/GtApply";
import {GtCalendar} from "../../elements/calendar/GtCalendar";
import {GtProfile} from "../../elements/profile/GtProfile";
import {GtDashboard} from "../../elements/dashboard/GtDashboard";

export const routes: RouteDeclaration[] = [
	["/dashboard", GtDashboard],

	["/profile(/:[0-9]+:user)?", GtProfile],

	["/calendar", GtCalendar],

	["/apply(/:[0-9]+:selected)?", GtApply],
	["/apply-guest", GtApplyRedirect],

	["/streams", GtStreams],
	["/streams/settings", GtStreamsSettings],
	["/streams/whitelist", GtStreamsWhitelist],

	["/roster", GtRoster],

	["/about", GtAbout],
	["/server-status", GtServerStatus],
	["/settings", GtSettings],
];
