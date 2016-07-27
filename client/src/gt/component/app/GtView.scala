package gt.component.app

import gt.component.GtHandler
import util.jsannotation.js
import xuen.Component

object GtView extends Component[GtView](
	selector = "gt-view",
	templateUrl = "/assets/imports/app.html"
)

@js class GtView extends GtHandler {
}
