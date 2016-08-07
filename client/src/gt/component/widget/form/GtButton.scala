package gt.component.widget.form

import gt.Router
import gt.component.GtHandler
import org.scalajs.dom.MouseEvent
import util.implicits._
import util.jsannotation.js
import xuen.Component
import xuen.rx.Var

object GtButton extends Component[GtButton](
	selector = "gt-button",
	templateUrl = "/assets/imports/widgets.html"
)

@js class GtButton extends GtHandler {
	/** Disables the button */
	// TODO: make this sane again once Scala.js DOM is fixed
	this.dyn.disabled = attribute[Boolean].dyn
	@inline def _disabled: Var[Boolean] = this.dyn.disabled.asInstanceOf[Var[Boolean]]

	/** If set, clicking the button will trigger the submit event on the enclosing form */
	val submit = attribute[Boolean]

	/** If set, clicking the button will trigger navigation */
	val goto = attribute[String]

	listen("click") { e: MouseEvent =>
		if (_disabled) {
			e.stopImmediatePropagation()
			e.preventDefault()
		} else if (goto.! != null) {
			Router.goto(goto.!)
		} else if (submit) {
			for (form <- Option(closest("gt-form").asInstanceOf[GtForm])) {
				form.submit()
			}
		}
	}
}