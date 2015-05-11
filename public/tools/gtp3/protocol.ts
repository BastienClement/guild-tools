/**
 * Magic numbers for the protocol
 */
export const enum Protocol {
	GTP3 = 0x47545033,
	FrameLimit = 65535,
	AckInterval = 8,
	BufferSoftLimit = 16,
	BufferHardLimit = 128,
	RequestAckCooldown = 4,
	OpenTimeout = 5000,
	ChannelsLimit = 65535,
	InflightRequests = 250,
	CompressLimit = 250
}

/**
 * Frame type codes
 */
export const enum FrameType {
	// Connection control
	HELLO = 0x10,
	HANDSHAKE = 0x11,
	RESUME = 0x12,
	SYNC = 0x13,
	ACK = 0x14,
	BYE = 0x15,

	// Connection messages
	IGNORE = 0x20,
	COMMAND = 0x21,

	// Channel control
	OPEN = 0x30,
	OPEN_SUCCESS = 0x31,
	OPEN_FAILURE = 0x32,
	DESTROY = 0x33,

	// Channel messages
	MESSAGE = 0x40,
	REQUEST = 0x41,
	SUCCESS = 0x42,
	FAILURE = 0x43,
	CLOSE = 0x44
}

/**
 * Command codes
 */
export const enum CommandCode {
	PING = 0x01,
	PONG = 0x02,
	REQUEST_ACK = 0x03
}
