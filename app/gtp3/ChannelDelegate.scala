package gtp3

trait ChannelDelegate {
	def message(msg: MessageFrame): Unit = {}
	def request(req: RequestFrame): Unit = {}
}
