package gtp3

object Protocol {
	final val GTP3 = 0x47545033
	final val FrameLimit = 65535
	final val AckInterval = 8
	final val BufferSoftLimit = 32
	final val BufferPauseLimit = 128
	final val BufferHardLimit = 256
	final val RequestAckCooldown = 4
	final val OpenTimeout = 10000
	final val ChannelsLimit = 65535
	final val InflightRequests = 250
	final val CompressLimit = 1200
	final val ReconnectAttempts = 5
}
