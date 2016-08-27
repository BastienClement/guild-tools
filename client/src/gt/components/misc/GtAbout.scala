package gt.components.misc

import gt.Server
import gt.components.widget.GtBox
import gt.components.{GtHandler, Tab, View}
import utils.jsannotation.js
import xuen.Component

object GtAbout extends Component[GtAbout](
	selector = "gt-about",
	templateUrl = "/assets/imports/views/about.html",
	dependencies = Seq(GtBox)
) with View {
	val module = "about"
	val tabs: TabGenerator = (selector, path, user) => Seq(
		Tab("About", "/about", active = true)
	)
}

@js class GtAbout extends GtHandler {
	val version = Server.version ~ (v => v.substring(0, 9 min v.length))
}


