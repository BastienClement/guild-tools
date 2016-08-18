package gt.component.widget.form

import gt.component.GtHandler
import org.scalajs.dom._
import org.scalajs.dom.raw.HTMLTextAreaElement
import scala.concurrent.duration._
import util.Debouncer
import util.implicits._
import util.jsannotation.js
import xuen.Component

object GtTextarea extends Component[GtTextarea](
	selector = "gt-textarea",
	templateUrl = "/assets/imports/widgets.html"
)

@js class GtTextarea extends GtHandler with AbstractInput with Interactive {
	/** Disables the textarea */
	// TODO: make this sane again once Scala.js DOM is fixed
	this.dyn.disabled = attribute[Boolean].dyn

	/** Prevents form submission if empty */
	val required = attribute[Boolean]

	/** Input label */
	val label = attribute[String]

	/** Proxy to input value */
	val value = model[String] := ""

	/** Error message */
	val error = property[String]

	/** Is an error message present? */
	val hasError = error ~ { e => e != null && !e.trim.isEmpty }

	/** Focuses the input */
	override def focus(): Unit = child.textarea.focus()

	/** Removes input focus */
	override def blur(): Unit = child.textarea.blur()

	// Update the cached value of the input
	private val update = Debouncer(200.millis) {
		value := child.as[HTMLTextAreaElement].textarea.value
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

	def mouseenter(): Unit = setAttribute("hover", "")
	def mouseleave(): Unit = removeAttribute("hover")

	// Track input state and value
	listen("focus", child.textarea) { e: Event => setAttribute("focused", "") }
	listen("blur", child.textarea) { e: Event => removeAttribute("focused") }
	listen("keyup", child.textarea) { e: Event => update.trigger() }
	listen("change", child.textarea) { e: Event => update.now() }
	listen("click", capture = true) { e: MouseEvent => e.stopPropagation() }
}

