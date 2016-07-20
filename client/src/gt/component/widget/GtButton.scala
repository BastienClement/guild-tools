package gt.component.widget

import scala.scalajs.js.annotation.ScalaJSDefined
import xuen.{Component, Handler}

object GtButton extends Component[GtButton](
	selector = "gt-button",
	templateUrl = "/assets/imports/widgets.html"
)

@ScalaJSDefined
class GtButton extends Handler {

}
