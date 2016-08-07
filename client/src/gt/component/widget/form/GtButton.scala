package gt.component.widget.form

import gt.component.GtHandler
import util.implicits._
import util.jsannotation.js
import xuen.Component

object GtButton extends Component[GtButton](
	selector = "gt-button",
	templateUrl = "/assets/imports/widgets.html"
)

@js class GtButton extends GtHandler {
	/** Disables the button */
	// TODO: make this sane again once Scala.js DOM is fixed
	this.dyn.disabled = attribute[Boolean].dyn
}
