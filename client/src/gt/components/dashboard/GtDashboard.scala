package gt.components.dashboard

import gt.components.profile.ProfileUser
import gt.components.widget.GtBox
import gt.components.{GtHandler, Tab, View}
import utils.jsannotation.js
import xuen.Component

object GtDashboard extends Component[GtDashboard](
	selector = "gt-dashboard",
	templateUrl = "/assets/imports/views/dashboard.html",
	dependencies = Seq(GtBox, DashboardNews, DashboardShoutbox, DashboardOnlines, ProfileUser)
) with View {
	val module = "dashboard"
	val tabs: TabGenerator = (selector, path, user) => Seq(
		Tab("Dashboard", "/dashboard", active = true)
	)
}

@js class GtDashboard extends GtHandler {

}

