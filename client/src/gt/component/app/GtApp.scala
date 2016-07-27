package gt.component.app

import gt.component.widget.{GtButton, GtDialog}
import util.jsannotation.js
import xuen.{Component, Handler}

/** Main application component */
object GtApp extends Component[GtApp](
	selector = "gt-app",
	templateUrl = "/assets/imports/app.html",
	dependencies = Seq(GtTitleBar, GtSidebar, GtView, GtDialog, GtButton)
)

@js class GtApp extends Handler {
	override def attached() = {
		println("attached")
	}
}

