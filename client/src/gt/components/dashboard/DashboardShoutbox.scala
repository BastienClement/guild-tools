package gt.components.dashboard

import gt.components.GtHandler
import gt.components.widget.GtBox
import utils.jsannotation.js
import xuen.Component

object DashboardShoutbox extends Component[DashboardShoutbox](
	selector = "dashboard-shoutbox",
	templateUrl = "/assets/imports/views/dashboard.html",
	dependencies = Seq(GtBox)
)

@js class DashboardShoutbox extends GtHandler {
}

