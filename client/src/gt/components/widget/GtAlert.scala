package gt.components.widget

import gt.components.GtHandler
import utils.jsannotation.js
import xuen.Component

object GtAlert extends Component[GtAlert](
	selector = "gt-alert",
	templateUrl = "/assets/imports/box.html",
	dependencies = Seq(GtBox)
)

@js class GtAlert extends GtHandler {
	val icon = attribute[String]
	val dark = attribute[Boolean]
}
