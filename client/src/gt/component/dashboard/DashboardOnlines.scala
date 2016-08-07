package gt.component.dashboard

import gt.component.GtHandler
import gt.component.widget.GtBox
import scala.scalajs.js
import util.jsannotation.js
import xuen.Component
import xuen.rx.Var

object DashboardOnlines extends Component[DashboardOnlines](
	selector = "dashboard-onlines",
	templateUrl = "/assets/imports/views/dashboard.html",
	dependencies = Seq(GtBox, DashboardOnlinesUser)
)

@js class DashboardOnlines extends GtHandler {
	val onlines = Var(js.Array[Unit]())
}
