package gtp3

abstract class ChannelRequest(val channel_type: String, val token: String, val socket: Socket) {
	protected var _channel: Option[Channel] = None
	def channel = _channel

	protected var _replied = false
	def replied = _replied

	def accept(handler: ChannelHandler): Channel
	def reject(code: Int, message: String): Unit
}
