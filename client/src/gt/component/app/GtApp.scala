package gt.component.app

import gt.component.GtHandler
import gt.component.widget.GtDialog
import gt.component.widget.form.GtButton
import rx.Var
import scala.scalajs.js.timers.setTimeout
import util.jsannotation.js
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
	def versionChanged(): Unit = showDead(2)
	def reset(): Unit = showDead(3)
}

