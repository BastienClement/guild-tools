package gt.component.app

import scala.scalajs.js.annotation.ScalaJSDefined
import xuen.{Component, Handler}

object GtSidebar extends Component[GtSidebar](
	selector = "gt-sidebar",
	templateUrl = "/assets/imports/app.html"
)

@ScalaJSDefined
class GtSidebar extends Handler {
}
