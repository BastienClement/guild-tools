package gt.component.widget

import facade.dom4.HTMLElement
import gt.component.GtHandler
import gt.component.widget.floating.{AbstractFloating, FloatingUtils, GtFloatingPlaceholder}
import org.scalajs.dom.{Event, MouseEvent, document, window}
import util.jsannotation.js
import xuen.Component

object GtContextMenu extends Component[GtContextMenu](
	selector = "gt-context-menu",
	templateUrl = "/assets/imports/floating.html"
)

@js class GtContextMenu extends GtHandler with AbstractFloating {
	val visible = attribute[Boolean] := false

	var transition: Boolean = false
	var placeholder: GtFloatingPlaceholder = null

	var parent: HTMLElement = null
	val passive = attribute[Boolean] := false
	val useclick = attribute[Boolean] := false

	val contextListener: scalajs.js.Function1[MouseEvent, Unit] = context _
	val closeListener: scalajs.js.Function1[Event, Unit] = close _
	val stopListener: scalajs.js.Function1[Event, Unit] = stop _

	override def attached(): Unit = if (!transition) {
		parent = FloatingUtils.parent(this)
		if (parent != null && !passive) {
			parent.addEventListener("contextmenu", contextListener)
			if (useclick) parent.addEventListener("click", contextListener)
		}
	}

	override def detached(): Unit = if (!transition) {
		if (parent != null) {
			parent.removeEventListener("contextmenu", contextListener)
			parent.removeEventListener("click", contextListener)
			parent = null
		}
	}

	def context(e: MouseEvent): Unit = if (!e.shiftKey) {
		FloatingUtils.lift(this, close)
		visible := true
		document.addEventListener("mousedown", closeListener)
		document.addEventListener("click", closeListener)
		addEventListener("mousedown", stopListener)
		fire("context-open")

		var x = e.clientX - 1
		var y = e.clientY - 1

		if (x + offsetWidth + 10 > window.innerWidth) x -= offsetWidth - 2
		if (y + offsetHeight + 10 > window.innerHeight) y -= offsetHeight - 2

		style.left = x + "px"
		style.top = y + "px"
	}

	def close(e: Event): Unit = {
		document.removeEventListener("mousedown", closeListener)
		document.removeEventListener("click", closeListener)
		removeEventListener("mousedown", stopListener)
		FloatingUtils.unlift(this)
		visible := false
		fire("context-close")
	}

	def stop(e: Event): Unit = e.stopPropagation()
}
