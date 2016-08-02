package gt.component.widget

import gt.component.GtHandler
import util.jsannotation.js
import xuen.Component

object BnetThumb extends Component[BnetThumb](
	selector = "bnet-thumb",
	templateUrl = "/assets/imports/bnet.html"
)

@js class BnetThumb extends GtHandler {
}

