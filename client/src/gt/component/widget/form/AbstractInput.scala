package gt.component.widget.form

import scala.scalajs.js
import util.jsannotation.js

/**
  * Common trait for every form-related input component with validatable input.
  */
@js trait AbstractInput extends js.Any {
	def reset(): Unit
	def validate(): Boolean
}
