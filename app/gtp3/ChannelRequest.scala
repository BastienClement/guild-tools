package gtp3

abstract class ChannelRequest(val channel_type: String, val token: String) {
	protected var _channel: Option[Channel] = None
	def channel = _channel

	protected var _replied = false
	def replied = _replied

	def accept(): Channel
	def reject(code: Int, message: String): Unit
}
