package channels

import gtp3._
import play.api.libs.json.{JsBoolean, JsValue}

import scala.concurrent.Future

object Auth extends ChannelAcceptor {
	def open(request: ChannelRequest) = request.accept(new Auth(request.socket))
}

class Auth(private val socket: Socket) extends ChannelHandler {
	def request(req: String, payload: Payload): Future[Payload] = {
		false
	}
}
