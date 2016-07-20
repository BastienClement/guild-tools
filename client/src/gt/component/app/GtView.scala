package gt.component.app

import scala.scalajs.js.annotation.ScalaJSDefined
import xuen.{Component, Handler}

object GtView extends Component[GtView](
	selector = "gt-view",
	templateUrl = "/assets/imports/app.html"
)

@ScalaJSDefined
class GtView extends Handler {
}
