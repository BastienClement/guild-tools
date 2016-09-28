package gt.components.streams

import gt.components.GtHandler
import gt.components.widget.GtAlert
import gt.services.StreamService
import org.scalajs
import org.scalajs.dom.raw.HTMLIFrameElement
import rx.Var
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.util.{Failure, Success}
import utils.jsannotation.js
import xuen.Component

object StreamPlayer extends Component[StreamPlayer](
	selector = "stream-player",
	templateUrl = "/assets/imports/views/streams.html",
	dependencies = Seq(GtAlert)
)

@js class StreamPlayer extends GtHandler {
	val streamService = service(StreamService)
	val stream = property[Int] ~> update _

	var player: HTMLIFrameElement = null
	var error = Var[String](null)

	def removePlayer(): Unit = {
		if (player != null) {
			player.parentNode.removeChild(player)
			player = null
		}
	}

	def update(user: Int): Unit = {
		error := null
		removePlayer()
		if (stream > 0) {
			streamService.requestTicket(stream).onComplete {
				case Success((ticket, key)) =>
					removePlayer()
					val iframe = scalajs.dom.document.createElement("iframe").asInstanceOf[HTMLIFrameElement]
					iframe.src = s"/clappr/$key?$ticket"
					iframe.asInstanceOf[js.Dynamic].allowFullscreen = true
					player = iframe
					shadow.appendChild(player)

				case Failure(e) =>
					error := e.getMessage
			}
		}
	}
}
