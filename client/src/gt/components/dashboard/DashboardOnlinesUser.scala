package gt.components.dashboard

import gt.components.GtHandler
import gt.components.widget.BnetThumb
import utils.jsannotation.js
import xuen.Component

object DashboardOnlinesUser extends Component[DashboardOnlinesUser](
	selector = "dashboard-onlines-user",
	templateUrl = "/assets/imports/views/dashboard.html",
	dependencies = Seq(BnetThumb)
)

@js class DashboardOnlinesUser extends GtHandler {
}
