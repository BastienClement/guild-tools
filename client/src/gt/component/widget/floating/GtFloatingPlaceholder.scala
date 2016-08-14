package gt.component.widget.floating

import gt.component.GtHandler
import org.scalajs.dom.Event
import scala.scalajs.js
import util.jsannotation.js
import xuen.Component

object GtFloatingPlaceholder extends Component[GtFloatingPlaceholder](
	selector = "gt-floating-placeholder"
)

@js class GtFloatingPlaceholder extends GtHandler {
	var listener: js.Function1[Event, Unit] = null

	override def detached(): Unit = fire("detached")

	def kill(): Unit = {
		removeEventListener("detached", listener)
		listener = null
	}
}
