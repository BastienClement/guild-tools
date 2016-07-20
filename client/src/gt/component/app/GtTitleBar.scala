package gt.component.app

import gt.component.widget.GtButton
import scala.scalajs.js.annotation.ScalaJSDefined
import xuen.{Component, Handler}

object GtTitleBar extends Component[GtTitleBar](
	selector = "gt-title-bar",
	templateUrl = "/assets/imports/app.html",
	dependencies = Seq(GtButton)
)

@ScalaJSDefined
class GtTitleBar extends Handler {
}
