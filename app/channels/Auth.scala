package channels

import gt.Global
import gtp3._
import play.api.libs.json.{JsBoolean, JsValue}
import scala.concurrent.duration._

import scala.concurrent.Future
import utils.Timeout

object Auth extends ChannelAcceptor {
	def open(request: ChannelRequest) = request.accept(new Auth(request.socket))
}

class Auth(private val socket: Socket) extends ChannelHandler {
	override val requests = Map(
		"logsin" -> login _
	)

	def login(payload: Payload): Future[Payload] = {
		false
	}
}
