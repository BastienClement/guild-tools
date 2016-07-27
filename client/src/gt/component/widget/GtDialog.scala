package gt.component.widget

import gt.component.GtHandler
import util.jsannotation.js
import xuen.Component

object GtDialog extends Component[GtDialog](
	selector = "gt-dialog",
	templateUrl = "/assets/imports/dialog.html"
)

@js class GtDialog extends GtHandler {

}
