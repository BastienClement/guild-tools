package gt.components.streams

import gt.components.widget.{GtAlert, GtBox}
import gt.components.{GtHandler, Tab, View}
import gt.services.StreamService
import rx.Var
import utils.jsannotation.js
import xuen.Component

/**
  * The composer view.
  */
object GtStreams extends Component[GtStreams](
	selector = "gt-streams",
	templateUrl = "/assets/imports/views/streams.html",
	dependencies = Seq(StreamItem, StreamViewers, StreamPlayer, GtBox, GtAlert)
) with View.Sticky {
	val module = "streams"

	val tabs: TabGenerator = (selector, path, user) => Seq(
		Tab("Streams", "/streams", true)
	)
}

@js class GtStreams extends GtHandler {
	val stream = service(StreamService)
	val list = stream.streams.values ~ (_.map(_.!))
	def hasStreams = list.nonEmpty
	val selected = Var(0)
	def selectStream(user: Int) = selected ~= (c => if (c == user) 0 else user)
}
