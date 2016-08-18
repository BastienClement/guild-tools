package gt.component.widget.form

import gt.component.GtHandler
import gt.component.widget.form.GtCheckbox.AnyZero
import org.scalajs.dom.MouseEvent
import rx.{Obs, Var}
import util.Zero
import util.implicits._
import util.jsannotation.js
import xuen.Component

object GtCheckbox extends Component[GtCheckbox](
	selector = "gt-checkbox",
	templateUrl = "/assets/imports/widgets.html"
) {
	implicit val AnyZero = new Zero[Any] {
		val zero: Any = null
	}
}

@js class GtCheckbox extends GtHandler with Interactive {
	/** Disables the input */
	// TODO: make this sane again once Scala.js DOM is fixed
	this.dyn.disabled = attribute[Boolean].dyn
	@inline def _disabled: Var[Boolean] = this.dyn.disabled.asInstanceOf[Var[Boolean]]

	/** Input label */
	val label = attribute[String]

	/**
	  * If defined, the checkbox act as a radio button
	  * If the radio button is selected, this attribute
	  * will be reflected to the value model.
	  */
	val radio = property[Any] := null

	/** Current checkbox state */
	val checked = model[Boolean]

	/** Proxy to input value */
	val value = model[Any]

	override def ready(): Unit = {
		if (hasAttribute("radio")) radio := getAttribute("radio")
	}

	checked ~> Obs { if (checked && radio.! != null) value := radio.! }
	private val sync = Obs { checked := (radio.! == value.!) }
	value ~> sync
	radio ~> sync

	def mouseenter(): Unit = setAttribute("hover", "")
	def mouseleave(): Unit = removeAttribute("hover")

	override def click(): Unit = {
		if (radio.! == null) {
			checked := !checked
		} else {
			checked := true
		}
	}

	// Capture enter key presses
	listen("click", capture = true) { e: MouseEvent =>
		e.stopPropagation()
		if (!_disabled) click()
	}
}
