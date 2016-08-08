package gt.component.widget.form

import gt.component.GtHandler
import org.scalajs.dom._
import util.implicits._
import util.jsannotation.js
import xuen.Component

object GtLabel extends Component[GtLabel](
	selector = "gt-label",
	templateUrl = "/assets/imports/widgets.html"
) {
	val interactives = "gt-button, gt-checkbox, gt-input"
}

@js class GtLabel extends GtHandler {
	listen("click") { e: MouseEvent => forEachInteractives(i => i.click()) }
	listen("mouseenter") { e: MouseEvent => forEachInteractives(i => i.mouseenter()) }
	listen("mouseleave") { e: MouseEvent => forEachInteractives(i => i.mouseleave()) }

	def forEachInteractives(fn: Interactive => Unit): Unit = {
		for (item <- querySelectorAll(GtLabel.interactives)) fn(item.asInstanceOf[Interactive])
	}
}


