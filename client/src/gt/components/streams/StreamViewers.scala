package gt.components.streams

import gt.components.GtHandler
import gt.components.calendar.CalendarUnitFrame
import gt.components.widget.GtBox
import utils.jsannotation.js
import xuen.Component

object StreamViewers extends Component[StreamViewers](
	selector = "stream-viewers",
	templateUrl = "/assets/imports/views/streams.html",
	dependencies = Seq(GtBox, CalendarUnitFrame)
)

@js class StreamViewers extends GtHandler {

}
