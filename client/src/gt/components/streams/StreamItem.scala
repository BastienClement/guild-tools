package gt.components.streams

import gt.components.GtHandler
import gt.components.widget.GtBox
import gt.services.RosterService
import models.StreamStatus
import utils.jsannotation.js
import xuen.Component

object StreamItem extends Component[StreamItem](
	selector = "stream-item",
	templateUrl = "/assets/imports/views/streams.html",
	dependencies = Seq(GtBox)
)

@js class StreamItem extends GtHandler {
	val roster = service(RosterService)

	val stream = property[StreamStatus]
	val main = stream ~! (s => roster.main(s.user))
	val viewers = stream ~ (s => s.viewersIds.size)
}
