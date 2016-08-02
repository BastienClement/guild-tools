package gt.component.widget

import gt.component.GtHandler
import scala.concurrent.duration._
import scala.scalajs.js
import scala.scalajs.js.timers._
import util.jsannotation.js
import xuen.Component

object GtTimeago extends Component[GtTimeago](
	selector = "gt-timeago"
)

@js class GtTimeago extends GtHandler {
	val date = attribute[String]
	private var ticker: SetIntervalHandle = null

	override def attached(): Unit = {
		clearInterval(ticker)
		ticker = setInterval(1.minute)(update _)
		update()
	}

	override def detached(): Unit = {
		clearInterval(ticker)
	}

	private def update(): Unit = {
		shadow.textContent = js.Dynamic.global.moment(date: String).fromNow().asInstanceOf[String]
	}
}
