package gt.component.widget

import gt.component.GtHandler
import util.jsannotation.js
import xuen.Component

object GtContextMenu extends Component[GtContextMenu](
	selector = "gt-context-menu",
	templateUrl = "/assets/imports/floating.html"
)

@js class GtContextMenu extends GtHandler {
}
