package gt.components.dashboard

import gt.components.GtHandler
import gt.components.widget.form.GtButton
import utils.jsannotation.js
import xuen.Component

object DashboardNewsFilter extends Component[DashboardNewsFilter](
	selector = "dashboard-news-filter",
	templateUrl = "/assets/imports/views/dashboard.html",
	dependencies = Seq(GtButton)
)

@js class DashboardNewsFilter extends GtHandler {
	val key = attribute[String]
	val active = model[Boolean]
}
