package gt.components.widget.form

import gt.components.GtHandler
import org.scalajs.dom._
import utils.implicits._
import utils.jsannotation.js
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


