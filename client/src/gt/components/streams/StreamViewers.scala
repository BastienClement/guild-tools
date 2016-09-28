package gt.components.streams

import gt.components.GtHandler
import gt.components.calendar.CalendarUnitFrame
import gt.components.widget.GtBox
import gt.services.{RosterService, StreamService}
import utils.jsannotation.js
import xuen.Component

object StreamViewers extends Component[StreamViewers](
	selector = "stream-viewers",
	templateUrl = "/assets/imports/views/streams.html",
	dependencies = Seq(GtBox, CalendarUnitFrame)
)

@js class StreamViewers extends GtHandler {
	val streamService = service(StreamService)
	val rosterService = service(RosterService)

	val stream = property[Int]

	val viewers = stream ~! { id =>
		streamService.streams.getOption(id)
	} ~ { ss =>
		ss.map(_.viewersIds).getOrElse(Iterable.empty)
	} ~ { vs =>
		vs.map(vid => rosterService.main(vid).id)
	}
}
