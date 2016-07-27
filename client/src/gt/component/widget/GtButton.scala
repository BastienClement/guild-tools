package gt.component.widget

import gt.component.GtHandler
import util.jsannotation.js
import xuen.Component

object GtButton extends Component[GtButton](
	selector = "gt-button",
	templateUrl = "/assets/imports/widgets.html"
)

@js class GtButton extends GtHandler {

}
