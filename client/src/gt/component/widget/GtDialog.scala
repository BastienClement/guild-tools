package gt.component.widget

import scala.scalajs.js.annotation.ScalaJSDefined
import xuen.{Component, Handler}

object GtDialog extends Component[GtDialog](
	selector = "gt-dialog",
	templateUrl = "/assets/imports/dialog.html"
)

@ScalaJSDefined
class GtDialog extends Handler {

}
