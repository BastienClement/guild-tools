package gt.components.dashboard

import gt.components.GtHandler
import gt.components.widget.GtBox
import rx.Var
import scala.scalajs.js
import utils.jsannotation.js
import xuen.Component

object DashboardOnlines extends Component[DashboardOnlines](
	selector = "dashboard-onlines",
	templateUrl = "/assets/imports/views/dashboard.html",
	dependencies = Seq(GtBox, DashboardOnlinesUser)
)

@js class DashboardOnlines extends GtHandler {
	val onlines = Var(js.Array[Unit]())
}

