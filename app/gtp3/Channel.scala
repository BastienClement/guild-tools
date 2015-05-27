package gtp3

class Channel(val socket: Socket, val id: Int, val sender_channel: Int, val handler: ChannelHandler) {
	def receive(frame: ChannelFrame) = {
		println(frame)
	}

	def close() = {
		socket.channelClosed(this)
	}
}
