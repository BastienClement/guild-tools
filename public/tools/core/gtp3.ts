import { deflate, inflate } from "pako";

import EventEmitter from "utils/eventemitter";
import BufferStream from "utils/bufferstream";
import Queue from "utils/queue";
import Deferred from "utils/deferred";
import NumberPool from "utils/numberpool";

const encoder = new TextEncoder("utf-8");
const decoder = new TextDecoder("utf-8");

/**
 * Types of frames
 */
const enum FrameType {
	// Connection control
	HELLO = 0xA0,
	RESUME = 0xA1,
	ACK = 0xA2,
	BYE = 0xA3,

	// Connection messages
	IGNORE = 0xB0,
	PING = 0xB1,
	PONG = 0xB2,
	ACK_REQ = 0xB3,

	// Channel control
	OPEN = 0xC0,
	OPEN_SUCCESS = 0xC1,
	OPEN_FAILURE = 0xC2,
	CLOSE = 0xC3,
	RESET = 0xC4,

	// Channel messages
	MESSAGE = 0xD0,
	REQUEST = 0xD1,
	SUCCESS = 0xD2,
	FAILURE = 0xD3
}

/**
 * Magic numbers for the protocol
 */
const enum Protocol {
	GTP3 = 0x47545033,
	PacketLimit = 65535,
	AckInterval = 8,
	BufferSoftLimit = 32,
	BufferHardLimit = 64,
	OpenTimeout = 5000,
	ChannelsLimit = 65535,
	InflightRequests = 250
}

/**
 * States of the socket
 */
const enum SocketState {
	Uninitialized, // Initial state
	Open,          // Open but not handshaked
	Ready,         // Fully working
	Reconnecting,  // Attempting to reconnect
	Closed         // Fully closed
}

/**
 * A tagged ArrayBuffer with a sequence id
 */
interface SequencedFrame {
	frame: ArrayBuffer;
	seq: number;
}

/**
 * A request to open a channel from the server
 */
interface ChannelRequest {
	accept(data?: ArrayBuffer): void;
	reject(code?: number, reason?: string): void;
}

/**
 * A function to call for a specific channel type open request
 */
interface ChannelOpenHandler {
	(request: ChannelRequest, token: string): void;
}

/**
 * Exception object for interrupting duplicated frame processing
 */
const DuplicatedFrame = new Error("Duplicated Frame");

/**
 * A GTP3 socket with many many featurez
 */
export class Socket extends EventEmitter {
	// The two parts of the 64 bits socket-id
	private id_high = 0;
	private id_low = 0;

	// The underlying websocket and connection state
	private ws: WebSocket = null;
	private state: SocketState = SocketState.Uninitialized;

	// Available channels
	private channels: Map<number, Channel> = new Map<number, Channel>();
	private channels_pending: Map<number, Deferred<Channel>> = new Map<number, Deferred<Channel>>();
	private channelid_pool: NumberPool = new NumberPool(Protocol.ChannelsLimit);

	// Last received frame sequence number
	private in_seq: number = 0;

	// Output queue and sequence numbers
	private out_seq: number = 0;
	private out_ack: number = 0;
	private out_buffer: Queue<SequencedFrame> = new Queue<SequencedFrame>();

	// Reconnect attempts counter
	private retry_count: number = 0;

	// Last ping time
	private ping_time: number = 0;
	public latency: number = 0;

	// Channel open handlers
	private open_handlers: Map<string, ChannelOpenHandler> = new Map<string, ChannelOpenHandler>();

	/**
	 * @param url The remote server to connect to
	 */
	constructor(private url: string) {
		super();
	}

	/**
	 * Connect to the server
	 */
	connect(): void {
		if (this.ws || this.state == SocketState.Closed) {
			throw new Error("Cannot connect() on an already open or closed socket");
		}

		// Create the websocket
		var ws = this.ws = new WebSocket(this.url, "GTP3-WS");
		ws.binaryType = "arraybuffer";

		// Reconnect on error or socket closed
		ws.onerror = ws.onclose = () => {
			this.reconnect();
		};

		// Handle message processing
		ws.onmessage = (ev) => {
			try {
				this.receive(ev.data);
			} catch (e) {
				// The DuplicatedFrame is thrown by the ack() method and is used to
				// interrupt message processing if the sequence number indicate that
				// the frame was already handled once. We just ignore it here.
				if (e === DuplicatedFrame) return;

				// Other exceptions should be propagated as ususal
				throw e;
			}
		};

		// Client initiate the handshake
		ws.onopen = () => {
			this.handshake();
		};
	}

	/**
	 * Generate the correct handshake frame given the current state
	 */
	private handshake(): void {
		let frame: BufferStream;

		switch (this.state) {
			case SocketState.Uninitialized: // Create a new socket
				frame = new BufferStream(5);
				frame.writeUint8(FrameType.HELLO);
				frame.writeUint32(Protocol.GTP3);
				break;

			case SocketState.Reconnecting: // Resume an already open socket
				frame = new BufferStream(11);
				frame.writeUint8(FrameType.RESUME);
				frame.writeUint32(this.id_high);
				frame.writeUint32(this.id_low);
				frame.writeUint16(this.in_seq);
				break;

			default: // Handshake cannot be called from this state
				throw new Error(`Cannot generate handshake from state '${this.state}'`);
		}

		this.state = SocketState.Open;
		this.retry_count = 0;
		this.emit("open");

		this.ws.send(frame.buffer());
	}

	/**
	 * Handle reconnection logic
	 */
	private reconnect(): void {
		// Transition from Ready to Reconnecting
		if (this.state == SocketState.Ready) {
			this.state = SocketState.Reconnecting;
			this.ws = null;
			this.emit("reconnecting");
		}

		// Check retry count and current state
		if (this.state != SocketState.Reconnecting || this.retry_count > 5) {
			this.emit("disconnected");
			this.close();
			return;
		}

		// Exponential backoff timer between reconnects
		const backoff_time = Math.pow(2, this.retry_count++) * 500;
		setTimeout(() => this.connect(), backoff_time);
	}

	/**
	 * Close the socket and send the BYE message
	 */
	close(code: number = 0, reason: string = ""): void {
		// Ensure the socket is not closed more than once
		if (this.state == SocketState.Closed) return;
		this.state = SocketState.Closed;

		// Emit events and messages
		this.emit("closed");
		this.sendBye(code, reason);

		// Actually close the WebSocket
		this.ws.close(code, reason);
		this.ws = null;
	}

	/**
	 * Put the socket back in ready state
	 */
	private ready(): void {
		this.state = SocketState.Ready;
		this.emit("ready");
	}

	/**
	 * Send a PING message and compute RTT latency
	 */
	ping(): void {
		this.ping_time = Date.now();
		this.sendControl(FrameType.PING);
	}

	/**
	 * Open a new channel on this socket
	 */
	openChannel(channel_type: string, token: string = "", parent: number = 0): Promise<Channel> {
		const id = this.channelid_pool.allocate();
		const deferred = new Deferred<Channel>();
		this.channels_pending.set(id, deferred);

		// Timeout for server to send OPEN_SUCCESS or OPEN_FAILURE
		setTimeout(() => deferred.reject(new Error("Timeout")), Protocol.OpenTimeout);

		// Send the open message to the server
		this.sendOpen(channel_type, id, parent, token);

		// Release channel ID if open fail
		deferred.promise.then(null, () => this.channelid_pool.release(id));

		return deferred.promise;
	}

	/**
	 * Open a new byte stream on this socket
	 */
	openStream(stream_type: string, token: string = ""): Promise<Stream> {
		return this.openChannel(stream_type, token).then((channel) => new Stream(channel));
	}

	/**
	 * Send acknowledgment to remote
	 * This function also ensure that we do not receive a frame multiple times
	 * and reduce network usage for acknowledgments by only sending one
	 * every AckInterval messages.
	 */
	private ack(frame: BufferStream): void {
		// Read sequence number from frame
		const seq = frame.readUint16();

		// Ensure the frame was not already received
		if (seq <= this.in_seq && (seq != 0 || this.in_seq == 0)) {
			throw DuplicatedFrame;
		}

		// Store the sequence number as the last received one
		this.in_seq = seq;

		// Only send an actual ACK if multiple of AckInterval
		if (seq % Protocol.AckInterval == 0) {
			this.sendAck(seq);
		}
	}

	/**
	 * Handle received acknowledgment processing
	 * @param seq The sequence number being acknowledged
	 */
	private processAck(seq: number): void {
		// Dequeue while the frame sequence id is less or equal to the acknowledged one
		// Also dequeue if the frame is simply greater than the last acknowledgment, this handle
		// the wrap-around case
		while (!this.out_buffer.empty()) {
			const f = this.out_buffer.peek();
			if (f.seq <= seq || f.seq > this.out_ack) {
				this.out_buffer.dequeue();
			} else {
				break;
			}
		}

		// Save the sequence number as the last one received
		this.out_ack = seq;
	}

	/**
	 * Receive a new frame
	 */
	private receive(buf: ArrayBuffer): void {
		const frame: BufferStream = new BufferStream(buf);
		const frame_type: FrameType = frame.readUint8();

		switch (frame_type) {
			// Connection control
			case FrameType.HELLO:
				return this.receiveHello(frame);

			case FrameType.RESUME:
				return this.receiveResume(frame);

			case FrameType.ACK:
				return this.receiveAck(frame);

			case FrameType.BYE:
				return this.receiveBye(frame);

			// Connection messages
			case FrameType.IGNORE:
				return;

			case FrameType.PING:
				return this.sendControl(FrameType.PONG);

			case FrameType.PONG:
				this.latency = Date.now() - this.ping_time;
				this.emit("latency", this.latency);
				return;

			case FrameType.ACK_REQ:
				return this.sendAck(this.in_seq);

			// Channel control
			case FrameType.OPEN:
				return this.receiveOpen(frame);

			case FrameType.OPEN_SUCCESS:
				return this.receiveOpenSuccess(frame);

			case FrameType.OPEN_FAILURE:
				return this.receiveOpenFailure(frame);

			case FrameType.RESET:
				return this.receiveReset(frame);

			// Channel messages
			case FrameType.MESSAGE:
			case FrameType.REQUEST:
			case FrameType.SUCCESS:
			case FrameType.FAILURE:
			case FrameType.CLOSE:
				return this.receiveChannelFrame(frame_type, frame);

			default:
				return this.protocolError();
		}
	}

	/**
	 * byte    HELLO
	 * uint32  magic_number
	 * uint32  socket_id_hi
	 * uint32  socket_id_lo
	 */
	private receiveHello(frame: BufferStream): void {
		const magic_number = frame.readUint32();

		if (this.state != SocketState.Open || magic_number != Protocol.GTP3)
			return this.protocolError();

		this.id_high = frame.readUint32();
		this.id_low = frame.readUint32();

		this.ready();
	}

	/**
	 * byte    RESUME
	 * uint16  last_received_id
	 */
	private receiveResume(frame: BufferStream): void {
		const seq_last = frame.readUint16();

		// Treat the resume message as an acknowledgment
		// This will remove any queued frames not acknowledged but in fact received
		this.processAck(seq_last);

		// Now, what's left in the buffer is only unreceived frame
		this.out_buffer.foreach(f => this.ws.send(f));

		this.ready();
	}

	/**
	 * byte    ACK
	 * uint16  last_received_id
	 */
	private receiveAck(frame: BufferStream): void {
		const last_received = frame.readUint16();
		this.processAck(last_received);
	}

	/**
	 * byte    BYE
	 * int16   code
	 * str16   message
	 */
	private receiveBye(frame: BufferStream): void {
		const code = frame.readInt16();
		const message = frame.readString16();
		this.close(code, message);
	}

	/**
	 * byte    OPEN
	 * uint16  seqence_number
	 * str8    channel_type
	 * uint16  sender_channel
	 * uint16  parent_channel
	 * str16   token
	 */
	private receiveOpen(frame: BufferStream): void {
		this.ack(frame);
		const channel_type = frame.readString8();
		const sender_channel = frame.readUint16();
		const parent_channel = frame.readUint16();
		const token = frame.readString16();

		let request_replied = false;

		const request: ChannelRequest = {
			accept: () => {
				if (request_replied) throw new Error();

				// Allocate a local Channel ID for this channel
				let id: number;
				try {
					id = this.channelid_pool.allocate();
				} catch (e) {
					request.reject(1, "Unable to allocate channel ID");
					throw e;
				}

				// Create the channel object
				const channel = new Channel(this, id, sender_channel);

				request_replied = true;
				this.channels.set(id, channel);
				this.sendOpenSuccess(channel);

				// Release the channel ID on close
				channel.on("closed", () => this.channelid_pool.release(id));

				return channel;
			},

			reject: (code: number, reason: string) => {
				if (request_replied) return;
				request_replied = true;
				this.sendOpenFailure(sender_channel, code, reason);
			}
		};

		if (this.open_handlers.has(channel_type)) {
			this.emit("channel-open-managed", request);
			try {
				this.open_handlers.get(channel_type)(request, token);
				if (!request_replied) throw null;
			} catch (e) {
				console.error("Channel creation failed", e);
				request.reject(1, "Channel initialization impossible");
			}
		} else {
			this.emit("channel-open", channel_type, request);
		}
	}

	/**
	 * byte    OPEN_SUCCESS
	 * uint16  seqence_number
	 * uint16  recipient_channel
	 * uint16  sender_channel
	 */
	private receiveOpenSuccess(frame: BufferStream): void {
		this.ack(frame);
		const recipient_channel = frame.readUint16();
		const sender_channel = frame.readUint16();

		if (!this.channels_pending.has(recipient_channel)) {
			return this.sendReset(recipient_channel);
		}

		const deferred = this.channels_pending.get(recipient_channel);
		this.channels_pending.delete(recipient_channel);

		const channel = new Channel(this, recipient_channel, sender_channel);
		deferred.resolve(channel);
	}

	/**
	 * byte    OPEN_FAILURE
	 * uint16  seqence_number
	 * uint16  recipient_channel
	 * int16   code
	 * str16   message
	 */
	private receiveOpenFailure(frame: BufferStream): void {
		this.ack(frame);
		const recipient_channel = frame.readUint16();
		const code = frame.readUint16();
		const message = frame.readString16();

		if (!this.channels_pending.has(recipient_channel)) {
			return this.sendReset(recipient_channel);
		}

		const deferred = this.channels_pending.get(recipient_channel);
		this.channels_pending.delete(recipient_channel);

		const error: any = new Error(message);
		error.code = code;

		deferred.reject(error);
	}

	/**
	 * byte    RESET
	 * uint16  sender_channel
	 */
	private receiveReset(frame: BufferStream): void {
		const sender_channel = frame.readUint16();

		// Close any channel matching the RESET message
		this.channels.forEach(channel => {
			if (channel.remote_id == sender_channel) {
				channel.close();
			}
		});
	}

	/**
	 * Generic channel frame handler
	 */
	private receiveChannelFrame(frame_type: number, frame: BufferStream): void {
		this.ack(frame);
		const recipient_channel = frame.readUint16();

		if (!this.channels.has(recipient_channel)) {
			return this.sendReset(recipient_channel);
		}

		const channel = this.channels.get(recipient_channel);
		channel._receive(frame_type, frame);
	}

	/**
	 * Handle force closing the socket due to protocol error
	 */
	private protocolError(): void {
		this.close();
		throw new Error("Protocol error");
	}

	/**
	 * byte    ACK
	 * uint16  last_received_id
	 */
	private sendAck(seq: number): void {
		const frame = new BufferStream(3);
		frame.writeUint8(FrameType.ACK);
		frame.writeUint16(seq);
		this._send(frame.buffer());
	}

	/**
	 * byte    BYE
	 * int16   code
	 * str16   message
	 */
	private sendBye(code: number, message: string): void {
		const message_buf = encoder.encode(message).buffer;
		const frame = new BufferStream(5 + message_buf.byteLength);
		frame.writeUint8(FrameType.BYE);
		frame.writeInt16(code);
		frame.writeUint16(message_buf.byteLength);
		frame.writeBuffer(message_buf);
		this._send(frame.compact());
	}

	/**
	 * byte    OPEN
	 * uint16  seqence_number
	 * str8    channel_type
	 * uint16  sender_channel
	 * uint16  parent_channel
	 * str16   token
	 */
	private sendOpen(channel_type: string, id: number, parent: number, token: string): void {
		const type_buffer = encoder.encode(channel_type).buffer;
		const token_buffer = encoder.encode(token).buffer;
		const frame = new BufferStream(10);
		frame.writeUint8(FrameType.OPEN_SUCCESS);
		frame.skip(2);
		frame.writeUint8(type_buffer.byteLength);
		frame.writeBuffer(type_buffer);
		frame.writeUint16(id);
		frame.writeUint16(parent);
		frame.writeUint16(token_buffer.byteLength);
		frame.writeBuffer(token_buffer);
		this._send(frame.buffer(), true);
	}

	/**
	 * byte    OPEN_SUCCESS
	 * uint16  sequence_id
	 * uint16  recipient_channel
	 * uint16  sender_channel
	 */
	private sendOpenSuccess(channel: Channel): void {
		const frame = new BufferStream(11);
		frame.writeUint8(FrameType.OPEN_SUCCESS);
		frame.skip(2);
		frame.writeUint16(channel.remote_id);
		frame.writeUint16(channel.local_id);
		this._send(frame.buffer(), true);
	}

	/**
	 * byte    OPEN_FAILURE
	 * uint16  sequence_id
	 * uint16  recipient_channel
	 * int16   code
	 * str16   message
	 */
	private sendOpenFailure(channel: number, code: number, reason: string): void {
		const reason_buffer = encoder.encode(reason).buffer;
		const frame = new BufferStream(9 + reason_buffer.byteLength);

		frame.writeUint8(FrameType.OPEN_FAILURE);
		frame.skip(2);
		frame.writeUint16(channel);
		frame.writeInt16(code);
		frame.writeUint16(reason_buffer.byteLength);
		frame.writeBuffer(reason_buffer);

		this._send(frame.compact(), true);
	}

	/**
	 * byte    RESET
	 * uint16  sender_channel
	 */
	private sendReset(channel: number): void {
		const frame = new BufferStream(3);
		frame.writeUint8(FrameType.RESET);
		frame.writeUint16(channel);
		this._send(frame.buffer());
	}

	/**
	 * Helper function for all simple frames
	 */
	private sendControl(frame_type: number): void {
		this._send(new Uint8Array([frame_type]).buffer);
	}

	/**
	 * Send a complete frame
	 */
	_send(frame: ArrayBuffer, seq: boolean = false): void {
		// Ensure a maximum frame size
		if (frame.byteLength > Protocol.PacketLimit) {
			throw new Error("Frame size limit exceeded");
		}

		// Add the sequence number to the frame if requested
		if (seq) {
			// Check the size of the output buffer
			const out_buffer_len = this.out_buffer.length();
			if (out_buffer_len > Protocol.BufferHardLimit) {
				throw new Error("Output buffer is full");
			} else if (out_buffer_len > Protocol.BufferSoftLimit) {
				this.sendControl(FrameType.ACK_REQ);
			}

			// Compute the next sequence number
			this.out_seq = (this.out_seq + 1) & 0xFFFF;

			// Tag the frame
			(new Uint16Array(frame, 1, 1))[0] = this.out_seq;

			// Push the frame in the output buffer for later replay
			this.out_buffer.enqueue({frame: frame, seq: this.out_seq});
		}

		if (this.state == SocketState.Ready) {
			this.ws.send(frame);
		}
	}

	/**
	 * Register a new channel open handler
	 */
	registerOpenHandler(channel_type: string, handler: ChannelOpenHandler): void {
		if (this.open_handlers.has(channel_type)) {
			throw new Error(`An open handler is already registered for channel type ${channel_type}`);
		}

		this.open_handlers.set(channel_type, handler);
	}

	/**
	 * Unregister an open handler previously registered
	 */
	unregisterOpenHandler(channel_type: string): void {
		this.open_handlers.delete(channel_type);
	}
}

/**
 * Channel states
 */
const enum ChannelState { Open, Closed }

/**
 * Frame flags indicating high-level encoding
 */
const enum PayloadFlags {
	COMPRESS = 0x01,
	UTF8DATA = 0x02,
	JSONDATA = 0x06, // Require UTF8DATA
	IGNORE = 0x80
}

/**
 * Channel implementation
 */
export class Channel extends EventEmitter {
	// Current channel state
	public state: ChannelState = ChannelState.Open;

	// The id of the next outgoing request
	private requestid_pool: NumberPool = new NumberPool(Protocol.InflightRequests);

	// Pending requests
	private requests: Map<number, Deferred<any>> = new Map<number, Deferred<any>>();

	// Default message flags
	private default_flags: number = 0;

	/**
	 * @param socket       The underlying socket object
	 * @param local_id     Local ID of this channel
	 * @param remote_id    Remote ID for this channel
	 */
	constructor(public socket: Socket,
	            public local_id: number,
	            public remote_id: number) {
		super();
	}

	/**
	 * Send a message without expecting a reply
	 */
	send(message: string, data: any = null, flags: number = this.default_flags): void {
		this.sendMessage(message, data, flags, false);
	}

	/**
	 * Send a request and expect a reply
	 */
	request<T>(request: string, data: any = null, flags: number = this.default_flags): Promise<T> {
		const id = this.requestid_pool.allocate();
		this.sendMessage(request, data, flags, id);

		const deferred = new Deferred<T>();
		this.requests.set(id, deferred);

		return deferred.promise;
	}

	/**
	 * Close the channel and attempt to flush output buffer
	 */
	close(): void {
		// Ensure we don't close the channel multiple times
		if (this.state == ChannelState.Closed) return;
		this.state = ChannelState.Closed;

		// Send close message to remote and local listeners
		this.sendClose();
		this.emit("closed");
	}

	/**
	 * Receive a channel specific frame
	 */
	_receive(frame_type: number, frame: BufferStream): void {
		switch (frame_type) {
			case FrameType.MESSAGE:
				return this.receiveMessage(frame, false);

			case FrameType.REQUEST:
				return this.receiveMessage(frame, true);

			case FrameType.SUCCESS:
				return this.receiveSuccess(frame);

			case FrameType.FAILURE:
				return this.receiveFailure(frame);

			case FrameType.CLOSE:
				return this.close();
		}
	}

	/**
	 * byte    MESSAGE | REQUEST
	 * uint16  seqence_number
	 * uint16  recipient_channel
	 *         ---
	 * str8    message | request
	 * uint8   [request_id]
	 * uint8   flags
	 * ...     payload
	 */
	private receiveMessage(frame: BufferStream, request: boolean): void {
		const message = frame.readString8();
		const request_id = request ? frame.readUint8() : 0;
		const payload = this.decodePayload<any>(frame);
	}

	/**
	 * byte    SUCCESS
	 * uint16  seqence_number
	 * uint16  recipient_channel
	 *         ---
	 * uint8   request_id
	 * uint8   flags
	 * ...     payload
	 */
	private receiveSuccess(frame: BufferStream): void {
		const request_id = frame.readUint8();
		if (!this.requests.has(request_id)) return;

		const payload = this.decodePayload(frame);
		const deferred = this.requests.get(request_id);

		deferred.resolve(payload);
	}

	/**
	 * byte    FAILURE
	 * uint16  seqence_number
	 * uint16  recipient_channel
	 *         ---
	 * uint8   request_id
	 * int16   code
	 * str16   message
	 * ...     payload
	 */
	private receiveFailure(frame: BufferStream): void {
		const request_id = frame.readUint8();
		if (!this.requests.has(request_id)) return;

		const code = frame.readInt16();
		const message = frame.readString16();

		const deferred = this.requests.get(request_id);

		const error: any = new Error(message);
		error.code = code;

		deferred.reject(error);
	}

	/**
	 * byte    MESSAGE | REQUEST
	 * uint16  seqence_number
	 * uint16  recipient_channel
	 * str8    message | request
	 * uint8   [request_id]
	 * uint8   flags
	 * ...     payload
	 */
	private sendMessage(message: string, data: any, flags: number, request: number | boolean): void {
		const is_request = request !== false;
		const header_length = is_request ? 8 : 7;
		const message_buf = encoder.encode(message).buffer;
		const payload = this.encodePayload(data, flags);

		const frame = new BufferStream(header_length + message_buf.byteLength + payload.byteLength);

		frame.writeUint8(is_request ? FrameType.REQUEST : FrameType.MESSAGE);
		frame.skip(2);
		frame.writeUint16(this.remote_id);
		frame.writeUint8(message_buf.byteLength);
		frame.writeBuffer(message_buf);
		if (is_request) frame.writeUint8(<number> request);
		frame.writeBuffer(payload);

		this.socket._send(frame.buffer(), true);
	}

	/**
	 * byte    CLOSE
	 * uint16  sequence_id
	 * uint16  recipient_channel
	 */
	private sendClose(): void {
		const frame = new BufferStream(5);
		frame.writeUint8(FrameType.CLOSE);
		frame.skip(2);
		frame.writeUint16(this.remote_id);
		this.socket._send(frame.buffer(), true);
	}

	/**
	 * Decode the frame payload data
	 */
	private decodePayload<T>(frame: BufferStream): T {
		const flags = frame.readUint8();

		if (flags & PayloadFlags.IGNORE) {
			return null;
		}

		let payload: any = frame.readBuffer();

		// Inflate compressed payload
		if (flags & PayloadFlags.COMPRESS) {
			payload = inflate(payload);
		}

		// Decode UTF-8 data
		if (flags & PayloadFlags.UTF8DATA) {
			payload = decoder.decode(payload);
		}

		// Decode JSON data
		if (flags & PayloadFlags.JSONDATA) {
			payload = JSON.parse(payload);
		}

		return <T> payload;
	}

	/**
	 * Encode payload data and flags
	 */
	private encodePayload(data: any, flags: number = 0): ArrayBuffer {
		if (data && (data.buffer || data) instanceof ArrayBuffer) {
			// Raw buffer
			data = data.buffer || data;
		} else if (typeof data === "string") {
			// String
			data = encoder.encode(data).buffer;
			flags |= PayloadFlags.UTF8DATA;
		} else if (data !== null && data !== void 0) {
			// Any other type will simply be JSON-encoded
			data = encoder.encode(JSON.stringify(data)).buffer;
			flags |= PayloadFlags.JSONDATA;
		}

		if (!data) {
			// No useful data
			return new Uint8Array([PayloadFlags.IGNORE]).buffer;
		} else if (flags & PayloadFlags.COMPRESS) {
			// Deflate payload
			data = deflate(data);
		}

		const payload = new BufferStream(1 + data.byteLength);
		payload.writeUint8(flags);
		payload.writeBuffer(data);

		return payload.buffer();
	}

	/**
	 * Tranform a message-based channel to a data-stream channel
	 */
	toStream(): Stream {
		return new Stream(this);
	}
}

/**
 * Data stream over message channel implementation
 */
export class Stream extends EventEmitter {
	constructor(private channel: Channel) {
		super();
	}
}
