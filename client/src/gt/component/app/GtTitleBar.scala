package gt.component.app

import gt.component.widget.form.GtButton
import gt.component.{GtHandler, Tab}
import gt.{Router, Server}
import org.scalajs.dom.{MouseEvent, document, window}
import rx.Var
import scala.scalajs.js.timers.setTimeout
import util.jsannotation.js
import xuen.Component

object GtTitleBar extends Component[GtTitleBar](
	selector = "gt-title-bar",
	templateUrl = "/assets/imports/app.html",
	dependencies = Seq(GtButton)
)

@js class GtTitleBar extends GtHandler {
	override def ready(): Unit = {
		// Remove the title bar if not launched as an app
		if (!app.standalone) {
			val ctrls = child.`window-controls`
			ctrls.parentNode.removeChild(ctrls)
		}
	}

	// ==========================
	// Loader

	// Track loading status
	val loading = Server.loading

	// ==========================
	// Tabs

	// Current visible tabs
	val tabs = Var(Nil: Seq[Tab])
	Router.onUpdate ~> { case (path, _, view) =>
		tabs := view.tabs(view.selector, path, app.user).filter(!_.hidden)
	}

	// Number of online players
	var onlines = Var(0)

	// ==========================
	// Panel

	// Panel status
	val panel = attribute[Boolean] := false
	var panelDebounce = false

	/** Opens the panel */
	def openPanel(): Unit = if (!panel) {
		panel := true
		style.zIndex = "20"
	}

	/** Absorbs clicks on the panel */
	def panelClicked(ev: MouseEvent): Unit = if (panel) {
		ev.stopImmediatePropagation()
		ev.preventDefault()
		closePanel()
	}

	/** Closes panel on mouseleave */
	def closePanel(): Unit = if (panel) {
		panel := false
		if (panelDebounce) {
			panelDebounce = true
			setTimeout(300) {
				if (!panel) style.zIndex = "10"
				panelDebounce = false
			}
		}
	}

	def reload(): Unit = document.location.reload()
	def downloadClient(): Unit = {}
	def devTools(): Unit = {}
	def logout(): Unit = {}
	def quit(): Unit = window.close()
}
