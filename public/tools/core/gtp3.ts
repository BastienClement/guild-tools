/// <reference path="../defs/pako.d.ts" />
/// <reference path="../defs/encoding.d.ts" />

import Pako = require("pako");

import EventEmitter = require("utils/eventemitter");
import BufferStream = require("utils/bufferstream");
import Queue = require("utils/queue");
import Deferred = require("utils/deferred");

const encoder = new TextEncoder("utf-8");
const decoder = new TextDecoder("utf-8");

/**
 * Types of frames
 */
const enum FrameType {
	// Connection control
	HELLO = 0xA0,
	RESUME = 0xA1,
	ACK = 0xAA,
	BYE = 0xAB,

	// Ping mechanism
	PING = 0xB1,
	HEARTBEAT = 0xBE,

	// Channel control
	OPEN = 0xC0,
	OPEN_SUCCESS = 0xC1,
	OPEN_FAILURE = 0xC2,
	WINDOW_ADJUST = 0xCA,
	CLOSE = 0xCC,
	RESET = 0xCD,
	EOF = 0xCE,

	// Datagrams
	MESSAGE = 0xD0,
	REQUEST = 0xD1,
	SUCCESS = 0xD2,
	FAILURE = 0xD3
}

/**
 * Magic number for the protocol
 */
const enum Protocol {
	GTP3 = 0x47545033,
	PayloadLimit = 65529,
	PacketLimit = 65535
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
	(c: ChannelRequest): void;
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
	private ws: WebSocket;
	private state: SocketState = SocketState.Uninitialized;

	// Available channels
	private channels: Map<number, Channel> = new Map<number, Channel>();
	private next_channel = 1;

	// Last received frame sequence number
	private in_seq = 0;
	private ack_interval: number = 16;

	// Output queue and sequence numbers
	private out_seq = 0;
	private out_ack = 0;
	private out_buffer: Queue<SequencedFrame> = new Queue<SequencedFrame>();

	// Reconnect attempts counter
	private retry_count = 0;

	// Last ping time
	private ping_time: number = 0;
	private latency: number = 0;

	// Channel open handlers
	private open_handlers: Map<string, ChannelOpenHandler> = new Map<string, ChannelOpenHandler>();

	/**
	 * @param url    The remote server to connect to
	 */
	constructor(private url: string) {
		super();
	}

	/**
	 * Connect to the server
	 */
	connect() {
		if (this.ws || this.state == SocketState.Closed) {
			throw new Error("Cannot connect() on an already open or closed socket");
		}

		// Create the websocket
		var ws = this.ws = new WebSocket(this.url, "GTP3/WS");
		ws.binaryType = "arraybuffer";

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
	private handshake() {
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
	private reconnect() {
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
	close(code: number = 0, reason: string = "") {
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
	private ready() {
		this.state = SocketState.Ready;
		this.emit("ready");
	}

	/**
	 * Send a PING message and compute RTT latency
	 */
	ping() {
		this.ping_time = Date.now();
		this.sendPing();
	}

	openChannel(channel_type: string, token: string = "") {

	}

	openStream() {

	}

	/**
	 * Send acknowledgment to remote
	 * This function also ensure that we do not receive a frame multiple times
	 * and reduce network bandwidth usage for acknowledgments by only sending one
	 * every `ack_interval` messages.
	 */
	private ack(frame: BufferStream) {
		// Read sequence number from frame
		const seq = frame.readUint16();

		// Ensure the frame was not already received
		if (seq <= this.in_seq && (seq != 0 || this.in_seq == 0)) {
			throw DuplicatedFrame;
		}

		// Store the sequence number as the last received one
		this.in_seq = seq;

		// Only send an actual ACK if multiple of ack_interval
		if (seq % this.ack_interval == 0) {
			this.sendAck(seq);
		}
	}

	/**
	 * Handle received acknowledgment processing
	 * @param seq The sequence number being acknowledged
	 */
	private processAck(seq: number) {
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
	private receive(buf: ArrayBuffer) {
		const frame: BufferStream = new BufferStream(buf);
		const frame_type: FrameType = frame.readUint8();

		switch (frame_type) {
			case FrameType.HELLO:
				return this.receiveHello(frame);

			case FrameType.RESUME:
				return this.receiveResume(frame);

			case FrameType.BYE:
				return this.receiveBye(frame);

			case FrameType.ACK:
				return this.receiveAck(frame);

			case FrameType.PING:
				return this.receivePing();

			case FrameType.HEARTBEAT:
				return this.receiveHeartbeat(frame);

			case FrameType.OPEN:
				return this.receiveOpen(frame);

			case FrameType.RESET:
				return this.receiveReset(frame);

			case FrameType.OPEN_SUCCESS:
			case FrameType.OPEN_FAILURE:
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
	private receiveHello(frame: BufferStream) {
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
	private receiveResume(frame: BufferStream) {
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
	private receiveAck(frame: BufferStream) {
		const last_received = frame.readUint16();
		this.processAck(last_received);
	}

	/**
	 * byte    BYE
	 * int16   code
	 * str16   message
	 */
	private receiveBye(frame: BufferStream) {
		const code = frame.readInt16();
		const message = frame.readString16();
		this.close(code, message);
	}

	/**
	 * byte    PING
	 */
	private receivePing() {
		// Reply with HEARTBEAT
		this.sendHeartbeat(true);
	}

	/**
	 * byte    HEARTBEAT
	 * bool    ping_reply
	 */
	private receiveHeartbeat(frame: BufferStream) {
		const ping_reply = frame.readBoolean();
		if (ping_reply) {
			this.latency = Date.now() - this.ping_time;
			this.emit("latency", this.latency);
		}
	}

	/**
	 * byte    OPEN
	 * uint16  seqence_number
	 * str8    channel_type
	 * uint16  sender_channel
	 * uint16  parent_channel
	 * str16   token
	 */
	private receiveOpen(frame: BufferStream) {
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
				const id = this.allocateChannelID();
				if (id < 0) {
					request.reject(1, "Unable to allocate channel ID");
					throw new Error("Unnable to allocate channel ID");
				}

				// Create the channel object
				const channel = new Channel(this, channel_type, id, sender_channel, ChannelState.Open);

				request_replied = true;
				this.channels.set(id, channel);
				this.sendOpenSuccess(channel);

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
				this.open_handlers.get(channel_type)(request);
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
	 * byte    RESET
	 * uint16  sender_channel
	 */
	private receiveReset(frame: BufferStream) {
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
	private receiveChannelFrame(frame_type: number, frame: BufferStream) {
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
	private protocolError() {
		this.close();
		throw new Error("Protocol error");
	}

	/**
	 * Allocate a new number for a channel
	 */
	private allocateChannelID() {
		// This implementation only allocate half of the channel ID space
		if (this.channels.size >= 32768) {
			return 0;
		}

		// Current ID
		const id = this.next_channel;

		// Search the next one
		do {
			this.next_channel = (this.next_channel + 1) & 0xFFFF;
		} while (this.channels.has(this.next_channel) || this.next_channel == 0);

		return id;
	}

	/**
	 * byte    ACK
	 * uint16  last_received_id
	 */
	private sendAck(seq: number) {
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
	private sendBye(code: number, message: string) {
		const frame = new BufferStream(5 + message.length * 2);
		frame.writeUint8(FrameType.BYE);
		frame.writeInt16(code);
		frame.writeString16(message);
		this._send(frame.compact());
	}

	/**
	 * byte    PING
	 */
	private sendPing() {
		const frame = new BufferStream(1);
		frame.writeUint8(FrameType.PING);
		this._send(frame.buffer());
	}

	/**
	 * byte    HEARTBEAT
	 * byte    ping_reply
	 */
	private sendHeartbeat(ping_reply?: boolean) {
		const frame = new BufferStream(2);
		frame.writeUint8(FrameType.HEARTBEAT);
		frame.writeBoolean(ping_reply);
		this._send(frame.buffer());
	}

	/**
	 * byte    OPEN_SUCCESS
	 * uint16  sequence_id
	 * uint16  recipient_channel
	 * uint16  sender_channel
	 */
	private sendOpenSuccess(channel: Channel) {
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
	private sendOpenFailure(channel: number, code: number, reason: string) {
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
	private sendReset(channel: number) {
		const frame = new BufferStream(3);
		frame.writeUint8(FrameType.RESET);
		frame.writeUint16(channel);
		this._send(frame.buffer());
	}

	/**
	 * Send a complete frame
	 */
	_send(frame: ArrayBuffer, seq?: boolean) {
		// Add the sequence number to the frame if requested
		if (seq) {
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
}

/**
 * Channel states
 */
const enum ChannelState {
	Pending, // Channel is currently pending acceptation from the server
	Open,    // Channel is open and usable
	Closed   // Channel is closed and cannot be used anymore
}

const enum FrameFlags {
	COMPRESS = 0x01,
	UTF8DATA = 0x02,
	JSONDATA = 0x06, // Includes UTF8DATA
}

/**
 * Channel implementation
 */
export class Channel extends EventEmitter {
	// The id of the next outgoing request
	private _next_rid = 0;

	private get next_requestid() {
		const id = this._next_rid;
		this._next_rid = (this._next_rid + 1) & 0xFF;
		return id;
	}

	/**
	 * @param socket          The underlying socket object
	 * @param channel_type    Type of the channel
	 * @param local_id        Local ID of this channel
	 * @param remote_id       Remote ID for this channel
	 * @param state           Current channel state
	 */
	constructor(public socket: Socket,
	            public channel_type: string,
	            public local_id: number,
	            public remote_id: number,
	            public state: ChannelState) {
		super();
	}

	/**
	 * Generate the next request id
	 * @returns {number}
	 */
	private nextRequestId() {

	}

	/**
	 * Send a message without waiting for reply
	 */
	send(message: string, data: any = null, flags: number = 0, request: boolean = false) {
		let payload: ArrayBuffer;

		if (data === null) {
			// No data provided
			payload = null;
		} else if (data && (data.buffer || data) instanceof ArrayBuffer) {
			// Raw buffer
			payload = data.buffer || data;
		} else if (typeof data === "string") {
			// String
			payload = encoder.encode(data);
			flags |= FrameFlags.UTF8DATA;
		} else {
			// Any other type will simply be JSON encoded
			payload = encoder.encode(JSON.stringify(data));
			flags |= FrameFlags.JSONDATA;
		}

		this.sendMessage(message, payload, flags, request ? this.nextRequestId() : false);
	}

	/**
	 * Send a request a wait for a reply
	 */
	request(request: string, data: any = null, flags: number = 0) {
		this.send(request, data, flags, true);
	}

	/**
	 * Close the channel and attempt to flush output buffer
	 */
	close() {
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
	_receive(frame_type: number, frame: BufferStream) {
		switch (frame_type) {
			case FrameType.OPEN_SUCCESS:
				return this.receiveOpenSuccess(frame);

			case FrameType.OPEN_FAILURE:
				return this.receiveOpenFailure(frame);

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
	 * byte    OPEN_SUCCESS
	 * uint16  seqence_number
	 * uint16  recipient_channel
	 *         ---
	 * uint16  sender_channel
	 */
	private receiveOpenSuccess(frame: BufferStream) {
		if (this.state != ChannelState.Pending) return;
		this.remote_id = frame.readUint16();
		this.state = ChannelState.Open;
		this.emit("open");
	}

	/**
	 * byte    OPEN_FAILURE
	 * uint16  seqence_number
	 * uint16  recipient_channel
	 *         ---
	 * int16   code
	 * str16   message
	 */
	private receiveOpenFailure(frame: BufferStream) {
		if (this.state != ChannelState.Pending) return;
		const code = frame.readUint16();
		const message = frame.readString16();
		this.state = ChannelState.Closed;
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
	private receiveMessage(frame: BufferStream, request: boolean) {
		const message = frame.readString8();
		const request_id = request ? frame.readUint8() : 0;
		const flags = frame.readUint8();
		let payload: any = frame.readBuffer();

		// Inflate compressed frames
		if (flags & FrameFlags.COMPRESS) {
			payload = Pako.inflate(payload);
		}

		// Decode UTF-8 data
		if (flags & FrameFlags.UTF8DATA) {
			payload = decoder.decode(payload);
		}

		// Decode JSON data
		if (flags & FrameFlags.JSONDATA) {
			payload = JSON.parse(payload);
		}
	}

	/**
	 * byte    SUCCESS
	 * uint16  seqence_number
	 * uint16  recipient_channel
	 *         ---
	 * ...     payload
	 */
	private receiveSuccess(frame: BufferStream) {

	}

	/**
	 * byte    FAILURE
	 * uint16  seqence_number
	 * uint16  recipient_channel
	 *         ---
	 * int16   code
	 * str16   message
	 * ...     payload
	 */
	private receiveFailure(frame: BufferStream) {
		const code = frame.readInt16();
		const message = frame.readString16();
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
	private sendMessage(message: string, payload: ArrayBuffer, flags: number, request: number | boolean) {
		const header_length = request === false ? 8 : 7;
		const message_buf = encoder.encode(message);
		const message_length = message_buf.length;
		const payload_length = payload ? payload.byteLength : 0;

		const frame = new BufferStream(header_length + message_length + payload_length);

		frame.writeUint8(request ? FrameType.REQUEST : FrameType.MESSAGE);
		frame.skip(2);
		frame.writeUint16(this.remote_id);
		frame.writeString8(message);
		if (request !== false) frame.writeUint8(<number>request);
		frame.writeUint8(flags);
		if (payload) frame.writeBuffer(payload);

		this.socket._send(frame.buffer(), true);
	}

	/**
	 * byte    CLOSE
	 * uint16  sequence_id
	 * uint16  recipient_channel
	 */
	private sendClose() {
		const frame = new BufferStream(5);
		frame.writeUint8(FrameType.CLOSE);
		frame.skip(2);
		frame.writeUint16(this.remote_id);
		this.socket._send(frame.buffer(), true);
	}
}
