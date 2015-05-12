package gtp3

object FrameType {
	// Connection control
	final val HELLO = 0x10
	final val HANDSHAKE = 0x11
	final val RESUME = 0x12
	final val SYNC = 0x13
	final val ACK = 0x14
	final val BYE = 0x15

	// Connection commands
	final val IGNORE = 0x20
	final val PING = 0x21
	final val PONG = 0x22
	final val REQUEST_ACK = 0x23

	// Channel control
	final val OPEN = 0x60
	final val OPEN_SUCCESS = 0x61
	final val OPEN_FAILURE = 0x62
	final val RESET = 0x63

	// Channel messages
	final val MESSAGE = 0x70
	final val REQUEST = 0x71
	final val SUCCESS = 0x72
	final val FAILURE = 0x73
	final val CLOSE = 0x74
}
