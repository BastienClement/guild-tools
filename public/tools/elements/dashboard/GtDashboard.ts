import {View} from "../../client/router/View";
import {PolymerElement} from "../../polymer/PolymerElement";
import {Inject, Element} from "../../polymer/Annotations";
import {Server} from "../../client/server/Server";
import {DashboardNews} from "./DashboardNews";
import {DashboardShoutbox} from "./DashboardShoutbox";
import {DashboardOnlines} from "./DashboardOnlines";
import {ProfileUser} from "../profile/ProfileUser";

@View("dashboard", () => [{title: "Dashboard", link: "/dashboard", active: true}])
@Element({
	selector: "gt-dashboard",
	template: "/assets/views/dashboard.html",
	dependencies: [DashboardNews, DashboardShoutbox, DashboardOnlines, ProfileUser]
})
export class GtDashboard extends PolymerElement {
	@Inject private server: Server;
}
