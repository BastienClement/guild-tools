package gt.component.app

import gt.component.widget.{GtButton, GtDialog}
import scala.scalajs.js.annotation.ScalaJSDefined
import xuen.{Component, Handler}

/** Main application component */
object GtApp extends Component[GtApp](
	selector = "gt-app",
	templateUrl = "/assets/imports/app.html",
	dependencies = Seq(GtTitleBar, GtSidebar, GtView, GtDialog, GtButton)
)

@ScalaJSDefined
class GtApp extends Handler {
	override def attached() = {
		println("attached")

		//console.log(scalajs.js.Object.getPrototypeOf(this).dyn.hello.toString())
		//hello()
	}
}

