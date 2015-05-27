package channels

import gtp3.{Socket, ChannelHandler, ChannelRequest, ChannelAcceptor}

object Auth extends ChannelAcceptor {
	def open(request: ChannelRequest) = request.accept(new Auth(request.socket))
}

class Auth(private val socket: Socket) extends ChannelHandler {

}
