package gt.component.app

import gt.component.GtHandler
import gt.component.widget.GtButton
import util.jsannotation.js
import xuen.Component

object GtTitleBar extends Component[GtTitleBar](
	selector = "gt-title-bar",
	templateUrl = "/assets/imports/app.html",
	dependencies = Seq(GtButton)
)

@js class GtTitleBar extends GtHandler {
}
