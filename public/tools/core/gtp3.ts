/// <reference path="../defs/pako.d.ts" />

import EventEmitter = require("utils/eventemitter");
import BufferStream = require("utils/bufferstream");
import Queue = require("utils/queue");

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
	UNKNOW = 0xCD,
	EOF = 0xCE,

	// Data trasmission
	DATA = 0xD0,
	DATA_EXT = 0xD1,

	// RPC
	REQUEST = 0xE0,
	SUCCESS = 0xE1,
	FAILURE = 0xE2
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
interface SequencedFrame extends ArrayBuffer {
	seq_id: number;
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
	// Note: public because this needs to be accessible by Channel and Encoder objects
	public ws: WebSocket;
	public encoder: SocketEncoder = new SocketEncoder(this);
	private state: SocketState = SocketState.Uninitialized;

	// Available channels
	private channels: Map<number, Channel> = new Map<number, Channel>();
	private next_channel = 0;

	// Last received frame sequence number
	private in_seq = 0;
	private ack_interval: number = 16;

	// Output queue and sequence numbers
	private out_seq = 0;
	private out_ack = 0;
	private out_buffer: Queue<SequencedFrame> = new Queue<SequencedFrame>(256);

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

		ws.onmessage = (ev) => {
			try {
				this.receive(ev.data);
			} catch (e) {
				if (e === DuplicatedFrame) return;
				throw e;
			}
		};

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
			{
				frame = new BufferStream(5);
				frame.writeUint8(FrameType.HELLO);
				frame.writeUint32(Protocol.GTP3);
				break;
			}

			case SocketState.Reconnecting: // Resume an already open socket
			{
				frame = new BufferStream(11);
				frame.writeUint8(FrameType.RESUME);
				frame.writeUint32(this.id_high);
				frame.writeUint32(this.id_low);
				frame.writeUint16(this.in_seq);
				break;
			}

			default: // Handshake cannot be called from this state
			{
				throw new Error(`Cannot generate handshake from state '${this.state}'`);
			}
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

		const backoff_time = Math.pow(2, this.retry_count++) * 500;
		setTimeout(() => this.connect(), backoff_time);
	}

	/**
	 * Close the socket and send the BYE message
	 */
	close(code: number = 0, reason: string = "") {
		if (this.state != SocketState.Closed) {
			this.encoder.sendBye(code, reason);
		}
		this.closed(code, reason);
	}

	/**
	 * Put the socked in closed state
	 */
	private closed(code: number = 0, reason: string = "") {
		this.emit("closed");
		this.state = SocketState.Closed;
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
	 * Send a PING message and compute RTT
	 */
	ping() {
		this.ping_time = Date.now();
		this.encoder.sendPing();
	}

	/**
	 * Send acknowledgment to remote
	 */
	private ack(frame: BufferStream) {
		// Read sequence number from frame
		const seq = frame.readUint16();

		// Ensure the frame was not already received
		if (seq < this.in_seq && seq != 0) {
			throw DuplicatedFrame;
		}

		// Store the sequence number as the last received one
		this.in_seq = seq;

		// Only send an actual ACK if multiple of ack_interval
		if (seq % this.ack_interval == 0) {
			this.encoder.sendAck(seq);
		}
	}

	/**
	 * Handle received acknowledgment processing
	 * @param seq The sequence number being acknowledged
	 */
	private processAck(seq: number) {
		// Dequeue while the frame sequence id is less or equal to the acknowledged one
		// Also dequeue if the frame is simply greater than the last acknowledgment, this handle
		// the wrap-around cases
		this.out_buffer.dequeueWhile(f => f.seq_id <= seq || f.seq_id > this.out_ack);
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
			case FrameType.ACK:
				return this.receiveAck(frame);
			case FrameType.BYE:
				return this.receiveBye(frame);

			case FrameType.PING:
				return this.receivePing();
			case FrameType.HEARTBEAT:
				return this.receiveHeartbeat(frame);

			case FrameType.OPEN:
				return this.receiveOpen(frame);
			case FrameType.OPEN_SUCCESS:
				return this.receiveOpenSuccess(frame);
			case FrameType.OPEN_FAILURE:
				return this.receiveOpenFailure(frame);
			case FrameType.CLOSE:
				return this.receiveClose(frame);
			case FrameType.UNKNOW:
				return this.receiveUnknow(frame);

			case FrameType.WINDOW_ADJUST:
			case FrameType.EOF:
			case FrameType.DATA:
			case FrameType.DATA_EXT:
			case FrameType.REQUEST:
			case FrameType.SUCCESS:
			case FrameType.FAILURE:
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
		this.encoder.sendHeartbeat(true);
	}

	/**
	 * byte    HEARTBEAT
	 * bool    ping_reply
	 */
	private receiveHeartbeat(frame) {
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
	 * uint32  window_size
	 * ...     user_data
	 */
	private receiveOpen(frame: BufferStream) {
		this.ack(frame);
		const channel_type = frame.readString8();
		const sender_channel = frame.readUint16();
		const window_size = frame.readUint32();

		let request_replied = false;

		const request: ChannelRequest = {
			channel_type: channel_type,
			open_frame: frame,

			accept: (local_window: number, data: ArrayBuffer = null) => {
				if (request_replied) return;

				const id = this.allocateChannelID();
				if (id < 0) {
					throw new Error("Unnable to allocate channel ID");
				}

				const channel = new Channel(this, {
					channel_type: channel_type,
					local_id: id,
					remote_id: sender_channel,
					in_window: 1048576, // 1 MB
					out_window: window_size,
					initial_state: ChannelState.Open
				});

				request_replied = true;
				this.channels.set(id, channel);
				this.encoder.sendOpenSuccess(channel, data);
			},

			reject: (code: number, reason: string, data: ArrayBuffer = null) => {
				if (request_replied) return;
				request_replied = true;
				this.encoder.sendOpenFailure(sender_channel, code, reason, data);
			}
		};

		if (this.open_handlers.has(channel_type)) {
			this.emit("channel-open-managed", request);
			try {
				this.open_handlers.get(channel_type)(request);
				if (!request_replied) {
					throw new Error("Open handler did not replied to the request");
				}
			} catch (e) {
				console.error("Channel creation failed", e);
				request.reject(1, "Channel initialization impossible");
			}
		} else {
			this.emit("channel-open", request);
		}
	}

	/**
	 * byte    OPEN_SUCCESS
	 * uint16  seqence_number
	 * uint16  recipient_channel
	 * uint16  sender_channel
	 * uint32  window_size
	 * ...     user_data
	 */
	private receiveOpenSuccess(frame: BufferStream) {
		const recipient_channel = frame.readUint16();
		const sender_channel = frame.readUint16();
		const window_size = frame.readUint32();
		const user_data = frame.readBuffer();

		if (!this.channels.has(recipient_channel)) {
			return this.encoder.sendUnknow(recipient_channel);
		}

		const channel = this.channels.get(recipient_channel);
		if (channel.state != ChannelState.Pending) return;

		channel.remote_id = sender_channel;
		channel.out_window = window_size;
		channel.state = ChannelState.Open;
		channel.emit("open-success", user_data);
	}

	/**
	 * byte    OPEN_FAILURE
	 * uint16  seqence_number
	 * uint16  recipient_channel
	 * int16   code
	 * str16   message
	 * ...     user_data
	 */
	private receiveOpenFailure(frame: BufferStream) {
		const recipient_channel = frame.readUint16();
		const code = frame.readInt16();
		const message = frame.readString16();
		const user_data = frame.readBuffer();

		if (!this.channels.has(recipient_channel)) {
			return this.encoder.sendUnknow(recipient_channel);
		}

		const channel = this.channels.get(recipient_channel);
		if (channel.state != ChannelState.Pending) return;

		channel.state = ChannelState.Closed;
		this.channels.delete(recipient_channel);
		channel.emit("open-failure", code, message, user_data);
	}

	/**
	 * byte    WINDOW_ADJUST
	 * uint16  seqence_number
	 * uint16  recipient_channel
	 * uint32  bytes
	 */
	private receiveWindowAdjust(frame: BufferStream) {
		const recipient_channel = frame.readUint16();
		const bytes = frame.readUint16();

		if (!this.channels.has(recipient_channel)) {
			return this.encoder.sendUnknow(recipient_channel);
		}

		const channel = this.channels.get(recipient_channel);
		channel.out_window += bytes;
	}

	/**
	 * byte    CLOSE
	 * uint16  seqence_number
	 * uint16  recipient_channel
	 */
	private receiveClose(frame: BufferStream) {
		const recipient_channel = frame.readUint16();

		if (!this.channels.has(recipient_channel)) {
			return this.encoder.sendUnknow(recipient_channel);
		}

		const channel = this.channels.get(recipient_channel);

		// Channel is already closed
		if (channel.state == ChannelState.Closed) return;

		// Flush channel and close
		channel.close();
	}

	/**
	 * byte    UNKNOW
	 * uint16  sender_channel
	 */
	private receiveUnknow(frame: BufferStream) {
		const sender_channel = frame.readUint16();
		this.channels.forEach(channel => {
			if (channel.remote_id == sender_channel) {
				channel._closed();
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
			return this.encoder.sendUnknow(recipient_channel);
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
			return -1;
		}

		// Current ID
		const id = this.next_channel;

		// Search the next one
		do {
			this.next_channel = (this.next_channel + 1) & 0xFFFF;
		} while (this.channels.has(this.next_channel));

		return id;
	}

	/**
	 * Emit a sequenced frame
	 */
	_send_seq(frame: ArrayBuffer) {
		this.out_seq = (this.out_seq + 1) & 0xFFFF;

		// Tag the frame
		const sframe = <SequencedFrame> frame;
		sframe.seq_id = (new Uint16Array(frame, 1, 1))[0] = this.out_seq;
		this.out_buffer.enqueue(sframe);

		// Send the frame if socket is ready
		if (this.state == SocketState.Ready) {
			this._send(frame);
		}
	}

	/**
	 * Emit an unsequenced frame without buffering
	 */
	_send(frame: ArrayBuffer) {
		if (this.ws) {
			this.ws.send(frame);
		}
	}
}

/**
 * Output encoder helper
 */
class SocketEncoder {
	constructor(private sock: Socket) {
	}

	/**
	 * byte    ACK
	 * uint16  last_received_id
	 */
	sendAck(seq: number) {
		const frame = new BufferStream(3);
		frame.writeUint8(FrameType.ACK);
		frame.writeUint16(seq);
		this.sock._send(frame.buffer());
	}

	/**
	 * byte    BYE
	 * uint16  last_received_id
	 */
	sendBye(code: number, message: string) {
		const frame = new BufferStream(3 + message.length * 2);
		frame.writeUint8(FrameType.BYE);
		frame.writeInt16(code);
		frame.writeString16(message);
		this.sock._send(frame.compact());
	}

	/**
	 * byte    PING
	 */
	sendPing() {
		const frame = new BufferStream(1);
		frame.writeUint8(FrameType.PING);
		this.sock._send(frame.buffer());
	}

	/**
	 * byte    HEARTBEAT
	 * byte    ping_reply
	 */
	sendHeartbeat(ping_reply?: boolean) {
		const frame = new BufferStream(2);
		frame.writeUint8(FrameType.HEARTBEAT);
		frame.writeBoolean(ping_reply);
		this.sock._send(frame.buffer());
	}

	/**
	 * byte    OPEN_SUCCESS
	 * uint16  sequence_id
	 * uint16  recipient_channel
	 * uint16  sender_channel
	 * uint32  window_size
	 * ...     user_data
	 */
	sendOpenSuccess(channel: Channel, data: ArrayBuffer) {
		const frame = new BufferStream(9 + data.byteLength);
		frame.writeUint8(FrameType.OPEN_SUCCESS);
		frame.skip(2);
		frame.writeUint16(channel.remote_id);
		frame.writeUint16(channel.local_id);
		frame.writeUint32(channel.in_window);
		if (data) frame.writeBuffer(data);
		this.sock.ws.send(frame.buffer());
	}

	/**
	 * byte    OPEN_FAILURE
	 * uint16  sequence_id
	 * uint16  recipient_channel
	 * int16   code
	 * str16   message
	 * ...     user_data
	 */
	sendOpenFailure(channel: number, code: number, reason: string, data: ArrayBuffer) {
		const frame = new BufferStream(7 + reason.length * 2 + data.byteLength);
		frame.writeUint8(FrameType.OPEN_FAILURE);
		frame.skip(2);
		frame.writeUint16(channel);
		frame.writeInt16(code);
		frame.writeString16(reason);
		if (data) frame.writeBuffer(data);
		this.sock.ws.send(frame.compact());
	}

	/**
	 * byte    CLOSE
	 * uint16  sequence_id
	 * uint16  recipient_channel
	 */
	sendClose(channel: number) {
		const frame = new BufferStream(3);
		frame.writeUint8(FrameType.CLOSE);
		frame.skip(2);
		frame.writeUint16(channel);
		this.sock.ws.send(frame.buffer());
	}

	/**
	 * byte    UNKNOW
	 * uint16  sender_channel
	 */
	sendUnknow(channel: number) {
		const frame = new BufferStream(3);
		frame.writeUint8(FrameType.UNKNOW);
		frame.writeUint16(channel);
		this.sock.ws.send(frame.buffer());
	}
}

/************** CHANNEL ******************************************************/

interface ChannelRequest {
	channel_type: string;
	open_frame: BufferStream;

	accept(window: number, data?: ArrayBuffer): void;
	reject(code: number, reason: string): void;
}

const enum ChannelState { Pending, Open, Closed }

interface ChannelConfig {
	channel_type: string;
	local_id: number;
	remote_id?: number;
	in_window: number;
	out_window: number;
	initial_state: ChannelState;
}

interface ChannelFrame extends ArrayBuffer {
	payload: number;
}

export class Channel extends EventEmitter {
	// Channel configuration
	public channel_type: string;
	public local_id: number;
	public remote_id: number;
	public out_window: number;
	public in_window: number;
	public state: ChannelState;

	// Output buffer
	private out_buffer: Queue<ChannelFrame> = new Queue<ChannelFrame>(64);

	constructor(public socket: Socket, conf: ChannelConfig) {
		super();
		this.channel_type = conf.channel_type;
		this.local_id = conf.local_id;
		this.remote_id = conf.remote_id;
		this.out_window = conf.out_window;
		this.in_window = conf.in_window;
		this.state = conf.initial_state;
	}

	flush() {
		while (!this.out_buffer.empty()) {
			const length = this.out_buffer.peek().payload;

			if (length > this.out_window) {
				// Not enough window space to send the frame
				break;
			}

			const buf = this.out_buffer.dequeue();
			this.out_window -= length;
		}
	}

	close() {
		this.state = ChannelState.Closed;
		this.flush();
		this.out_buffer.clear();
		this.socket.encoder.sendClose(this.remote_id);
	}

	_closed() {
		this.emit("closed");
	}

	send(data: ArrayBuffer, extended_type: number = -1, dgram?: boolean) {
		if (this.out_queue.full()) {
			throw new Error("Output buffer for channel is full");
		}

		const len = data.byteLength;
		const extended: boolean = extended_type >= 0;

		if (len > Protocol.PayloadLimit) {
			throw new Error(`Message payload size is too large: ${len} (Payload limit at ${Protocol.PayloadLimit})`);
		}

		let frame;
		if (extended) {
			frame = new BufferStream(len + 6);
			frame.writeUint8(FrameType.DATA_EXT);
			frame.writeUint16(this.remote_id);
			frame.writeUint16(extended_type);
		} else {
			frame = new BufferStream(len + 4);
			frame.writeUint8(FrameType.DATA);
			frame.writeUint16(this.remote_id);
		}

		frame.writeBuffer(data);

		// Enqueue the frame
		this.out_buffer.enqueue(frame);
		this.buffered_bytes += frame.byteLength;

		// Auto flushing
		if (this.mode == ChannelMode.Datagram ||
			this.out_buffer.full() ||
			this.buffered_bytes >= this.stream_flush_bytes) {
			this.flush();
		}
	}

	_receive(frame_type: number, frame: BufferStream) {

	}

	/**
	 * byte    DATA
	 * uint16  seqence_number
	 * uint16  recipient_channel
	 * ...     data
	 */
	private receiveData(frame: BufferStream) {
		const recipient_channel = frame.readUint16();

		if (!this.channels.has(recipient_channel)) {
			return this.encoder.sendUnknow(recipient_channel);
		}

		const channel = this.channels.get(recipient_channel);

		throw new Error("Unimplemented");
	}

	/**
	 * byte    DATA_EXT
	 * uint16  seqence_number
	 * uint16  recipient_channel
	 * uint16  data_type
	 * ...     data
	 */
	private receiveDataExt(frame: BufferStream) {
		const recipient_channel = frame.readUint16();
		const data_type = frame.readUint16();

		if (!this.channels.has(recipient_channel)) {
			return this.encoder.sendUnknow(recipient_channel);
		}

		const channel = this.channels.get(recipient_channel);

		throw new Error("Unimplemented");
	}

	/**
	 * byte    REQUEST
	 * uint16  seqence_number
	 * uint16  recipient_channel
	 * str8    request
	 * byte    want_reply
	 * ...     payload
	 */
	private receiveRequest(frame: BufferStream) {
		const recipient_channel = frame.readUint16();
		const request = frame.readString8();

		if (!this.channels.has(recipient_channel)) {
			return this.encoder.sendUnknow(recipient_channel);
		}

		const channel = this.channels.get(recipient_channel);

		throw new Error("Unimplemented");
	}

	/**
	 * byte    SUCCESS
	 * uint16  seqence_number
	 * uint16  recipient_channel
	 * ...     payload
	 */
	private receiveSuccess(frame: BufferStream) {
		const recipient_channel = frame.readUint16();

		if (!this.channels.has(recipient_channel)) {
			return this.encoder.sendUnknow(recipient_channel);
		}

		const channel = this.channels.get(recipient_channel);

		throw new Error("Unimplemented");
	}

	/**
	 * byte    FAILURE
	 * uint16  seqence_number
	 * uint16  recipient_channel
	 * int16   code
	 * str16   message
	 * ...     payload
	 */
	private receiveFailure(frame: BufferStream) {
		const recipient_channel = frame.readUint16();
		const code = frame.readInt16();
		const message = frame.readString16();

		if (!this.channels.has(recipient_channel)) {
			return this.encoder.sendUnknow(recipient_channel);
		}

		const channel = this.channels.get(recipient_channel);

		throw new Error("Unimplemented");
	}
}
