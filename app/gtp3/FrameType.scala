package gtp3

object FrameType {
	// Connection control
	final val HELLO = 0x10
	final val HANDSHAKE = 0x11
	final val RESUME = 0x12
	final val SYNC = 0x13
	final val ACK = 0x14
	final val BYE = 0x15

	// Connection messages
	final val IGNORE = 0x20
	final val COMMAND = 0x21

	// Channel control
	final val OPEN = 0x30
	final val OPEN_SUCCESS = 0x31
	final val OPEN_FAILURE = 0x32
	final val DESTROY = 0x33

	// Channel messages
	final val MESSAGE = 0x40
	final val REQUEST = 0x41
	final val SUCCESS = 0x42
	final val FAILURE = 0x43
	final val CLOSE = 0x44
}
