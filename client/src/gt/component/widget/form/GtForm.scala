package gt.component.widget.form

import gt.component.GtHandler
import rx.Rx
import util.Microtask
import util.implicits._
import util.jsannotation.js
import xuen.Component

object GtForm extends Component[GtForm](
	selector = "gt-form"
) {
	final val inputElements = "gt-input"
}

@js class GtForm extends GtHandler {
	def reset(): Unit = Rx.atomically {
		querySelectorAll(GtForm.inputElements).foreach { node =>
			node.asInstanceOf[AbstractInput].reset()
		}
	}

	def isValid: Boolean = {
		var valid = true
		querySelectorAll(GtForm.inputElements).foreach { node =>
			if (!node.asInstanceOf[AbstractInput].validate()) valid = false
		}
		valid
	}

	def submit(): Unit = if (isValid) {
		Microtask.schedule(fire("submit"))
	}
}

