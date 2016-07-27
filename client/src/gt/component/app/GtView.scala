package gt.component.app

import util.jsannotation.js
import xuen.{Component, Handler}

object GtView extends Component[GtView](
	selector = "gt-view",
	templateUrl = "/assets/imports/app.html"
)

@js class GtView extends Handler {
}
