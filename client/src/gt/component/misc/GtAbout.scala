package gt.component.misc

import gt.Server
import gt.component.widget.GtBox
import gt.component.{GtHandler, Tab, View}
import util.jsannotation.js
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


