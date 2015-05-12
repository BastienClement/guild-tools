package gtp3

class Channel(val socket: Socket, val id: Int, val sender_channel: Int) {
	def receive(frame: ChannelFrame) = {

	}
}
