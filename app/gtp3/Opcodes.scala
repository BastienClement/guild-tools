package gtp3

object Opcodes {
	// Connection control
	final val HELLO = 0x10
	final val RESUME = 0x11
	final val ACK = 0x12
	final val BYE = 0x13

	// Connection messages
	final val IGNORE = 0x20
	final val PING = 0x21
	final val PONG = 0x22
	final val ACK_REQ = 0x23

	// Channel control
	final val OPEN = 0x30
	final val OPEN_SUCCESS = 0x31
	final val OPEN_FAILURE = 0x32
	final val CLOSE = 0x33
	final val RESET = 0x34

	// Channel messages
	final val MESSAGE = 0x40
	final val REQUEST = 0x41
	final val SUCCESS = 0x42
	final val FAILURE = 0x43
}
