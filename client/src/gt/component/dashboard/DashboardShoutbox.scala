package gt.component.dashboard

import gt.component.GtHandler
import gt.component.widget.GtBox
import util.jsannotation.js
import xuen.Component

object DashboardShoutbox extends Component[DashboardShoutbox](
	selector = "dashboard-shoutbox",
	templateUrl = "/assets/imports/views/dashboard.html",
	dependencies = Seq(GtBox)
)

@js class DashboardShoutbox extends GtHandler {
}

