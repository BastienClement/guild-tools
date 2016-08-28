package gt.components.widget.form

import gt.components.GtHandler
import gt.components.widget.form.GtCheckbox.AnyZero
import utils.jsannotation.js
import xuen.Component

object GtCheckboxGroup extends Component[GtCheckboxGroup](
	selector = "gt-checkbox-group",
	templateUrl = "/assets/imports/widgets.html"
)

@js class GtCheckboxGroup extends GtHandler {
	/** Input label */
	val label = attribute[String]
}
