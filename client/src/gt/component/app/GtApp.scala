package gt.component.app

import gt.component.GtHandler
import gt.component.widget.{GtButton, GtDialog}
import util.jsannotation.js
import xuen.Component

/** Main application component */
object GtApp extends Component[GtApp](
	selector = "gt-app",
	templateUrl = "/assets/imports/app.html",
	dependencies = Seq(GtTitleBar, GtSidebar, GtView, GtDialog, GtButton)
)

@js class GtApp extends GtHandler {
	override def attached() = {
		println("attached")
	}
}

