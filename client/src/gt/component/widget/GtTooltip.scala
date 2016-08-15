package gt.component.widget

import facade.dom4.HTMLElement
import gt.component.GtHandler
import gt.component.widget.floating.{AbstractFloating, FloatingUtil, GtFloatingPlaceholder}
import org.scalajs.dom.{Event, MouseEvent, window}
import scala.scalajs.js
import util.jsannotation.js
import xuen.Component

object GtTooltip extends Component[GtTooltip](
	selector = "gt-tooltip",
	templateUrl = "/assets/imports/floating.html"
)

@js class GtTooltip extends GtHandler with AbstractFloating {
	val visible = attribute[Boolean] := false
	val width = (attribute[Int] := 300) ~>> (w => style.maxWidth = w + "px")

	var parent: HTMLElement = null
	var transition: Boolean = false
	var placeholder: GtFloatingPlaceholder = null

	val enterListener: js.Function1[MouseEvent, Unit] = show _
	val leaveListener: js.Function1[Event, Unit] = hide _
	val moveListener: js.Function1[MouseEvent, Unit] = move _

	override def attached(): Unit = if (!transition) {
		parent = FloatingUtil.parent(this)
		if (parent != null) {
			parent.addEventListener("mouseenter", enterListener)
			parent.addEventListener("mouseleave", leaveListener)
		}
	}

	override def detached(): Unit = if (!transition) {
		if (parent != null) {
			parent.removeEventListener("mouseenter", enterListener)
			parent.removeEventListener("mouseleave", leaveListener)
			parent = null
		}
	}

	def show(e: MouseEvent): Unit = if (!visible) {
		FloatingUtil.lift(this, hide)
		parent.addEventListener("mousemove", moveListener)
		visible := true
		move(e)
		fire("tooltip-show")
	}

	def hide(e: Event): Unit = if (visible) {
		parent.removeEventListener("mousemove", moveListener)
		FloatingUtil.unlift(this)
		visible := false
		fire("tooltip-hide")
	}

	def move(e: MouseEvent): Unit = if (visible) {
		var x = e.clientX + 10
		var y = window.innerHeight - e.clientY + 10

		if (x + offsetWidth + 20 > window.innerWidth) {
			x -= offsetWidth + 20
		}

		if (y + offsetHeight + 20 > window.innerHeight) {
			y -= offsetHeight + 20
		}

		style.left = x + "px"
		style.bottom = y + "px"
	}
}
