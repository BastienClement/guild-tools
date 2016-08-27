package gt.components.app

import gt.components.GtHandler
import gt.components.widget.GtDialog
import gt.components.widget.form.GtButton
import rx.Var
import scala.scalajs.js.timers.setTimeout
import utils.jsannotation.js
import xuen.Component

/**
  * The main application component
  */
object GtApp extends Component[GtApp](
	selector = "gt-app",
	templateUrl = "/assets/imports/app.html",
	dependencies = Seq(GtTitleBar, GtSidebar, GtView, GtDialog, GtButton)
)

@js class GtApp extends GtHandler {
	// Sticky dialog
	def dialog = child.as[GtDialog].disconnected

	// Death status
	val dead = Var(false)
	val cause = Var(0)
	val details = Var(null: String)

	private def showDisconnected(state: Boolean): Unit = if (!dead) {
		if (state) dialog.show(true)
		else setTimeout(1000) {
			if (!dead) dialog.hide()
		}
	}

	private def showDead(cause: Int): Unit = if (!dead) {
		dead := true
		this.cause := cause
		if (!dialog.shown) dialog.show(true)
	}

	def connected(): Unit = showDisconnected(false)
	def reconnecting(): Unit = showDisconnected(true)
	def disconnected(): Unit = showDead(1)
	def reset(): Unit = showDead(3)
}

