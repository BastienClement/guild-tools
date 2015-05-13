/**
 * Magic numbers for the protocol
 */
export const enum Protocol {
	GTP3 = 0x47545033,
	FrameLimit = 65535,
	AckInterval = 8,
	BufferSoftLimit = 16,
	BufferPauseLimit = 64,
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
	PING = 0x21,
	PONG = 0x22,
	REQUEST_ACK = 0x23,

	// Channel control
	OPEN = 0x60,
	OPEN_SUCCESS = 0x61,
	OPEN_FAILURE = 0x62,
	RESET = 0x63,

	// Channel messages
	MESSAGE = 0x70,
	REQUEST = 0x71,
	SUCCESS = 0x72,
	FAILURE = 0x73,
	CLOSE = 0x74
}
