package gt.component.dashboard

import gt.component.GtHandler
import gt.component.widget.BnetThumb
import util.jsannotation.js
import xuen.Component

object DashboardOnlinesUser extends Component[DashboardOnlinesUser](
	selector = "dashboard-onlines-user",
	templateUrl = "/assets/imports/views/dashboard.html",
	dependencies = Seq(BnetThumb)
)

@js class DashboardOnlinesUser extends GtHandler {
}
