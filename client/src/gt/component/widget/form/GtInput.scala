package gt.component.widget.form

import gt.component.GtHandler
import org.scalajs.dom.Event
import org.scalajs.dom.raw.{HTMLInputElement, KeyboardEvent}
import scala.concurrent.duration._
import util.Debouncer
import util.implicits._
import util.jsannotation.js
import xuen.Component

object GtInput extends Component[GtInput](
	selector = "gt-input",
	templateUrl = "/assets/imports/widgets.html"
)

@js class GtInput extends GtHandler with AbstractInput {
	/** Disables the input */
	// TODO: make this sane again once Scala.js DOM is fixed
	this.dyn.disabled = attribute[Boolean].dyn

	/** Prevents form submission if empty */
	val required = attribute[Boolean]

	/** Input type */
	val `type` = attribute[String] := "text"

	/** Input label */
	val label = attribute[String]

	/** Proxy to input value */
	val value = model[String] := ""

	/** Error message */
	val error = property[String]

	/** Is an error message present? */
	val hasError = error ~ { e => e != null && !e.trim.isEmpty }

	/** Focuses the input */
	override def focus(): Unit = child.input.focus()

	/** Removes input focus */
	override def blur(): Unit = child.input.blur()

	// Update the cached value of the input
	private val update = Debouncer(200.millis) {
		value := child.as[HTMLInputElement].input.value
	}

	def reset(): Unit = {
		value := ""
		error := null
	}

	def validate(): Boolean = {
		if (required && value.matches("^\\s*$")) {
			error := "This field is required"
			focus()
			false
		} else {
			error := null
			true
		}
	}

	// Track input state and value
	listen("focus", child.input) { e: Event => setAttribute("focused", "") }
	listen("blur", child.input) { e: Event => removeAttribute("focused") }
	listen("keyup", child.input) { e: Event => update.trigger() }
	listen("change", child.input) { e: Event => update.now() }

	// Capture enter key presses
	listen("keypress", child.input) { e: KeyboardEvent =>
		if (e.keyCode == 13) {
			e.preventDefault()
			update.now()
			for (form <- Option(closest("gt-form").asInstanceOf[GtForm])) {
				blur()
				form.submit()
			}
		}
	}
}

