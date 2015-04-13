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
const enum ProtocolMagic {
	GTP3 = 0x47545033
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
interface SequencedArrayBuffer extends ArrayBuffer {
	seq_id: number;
}

/**
 * Configuration options for socket
 */
interface SocketOptions {
	bufferSize?: number; // Size of the output replay buffer
	ackInterval?: number; // Send ACKs every X message
}

/**
 * A GTP3 socket with many many featurez
 */
class Socket extends EventEmitter {
	// The two parts of the 64 bits socket-id
	private id_high = 0;
	private id_low = 0;

	// The underlying websocket and connection state
	// Note: public because this needs to be accessible by Channel and Encoder objects
	public ws: WebSocket;
	public encoder: SocketEncoder = new SocketEncoder(this);
	private state: SocketState = SocketState.Uninitialized;

	// Available channels
	private channels = new Map<number, Channel>();
	private next_channel = 0;

	// Last received frame sequence number
	private in_seq = 0;

	// Output queue and sequence numbers
	private out_seq = 0;
	private out_ack = 0;
	private out_buffer: Queue<SequencedArrayBuffer>;

	// Reconnect attempts counter
	private retry_count = 0;

	// Channel open handlers
	private open_handlers = new Map<string, (c: Channel) => void>();

	/**
	 * @param url    The remote server to connect to
	 * @param opts    Socket configuration object
	 */
	constructor(private url: string, opts: SocketOptions = {}) {
		super();

		// Initialize queue
		this.out_buffer = new Queue<SequencedArrayBuffer>(opts.bufferSize || 16);
	}

	/**
	 * Connect to the server
	 */
	connect() {
		if (this.ws || this.state == SocketState.Closed) {
			throw new Error("Cannot connect() on a open or closed socket");
		}

		// Create the websocket
		var ws = this.ws = new WebSocket(this.url, "GTP3/WS");
		ws.binaryType = "arraybuffer";

		// Event bindings
		ws.onerror = ws.onclose = () => this.reconnect();
		ws.onmessage = (ev) => this.receive(ev.data);
		ws.onopen = () => this.handshake();
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
				frame.writeUint32(ProtocolMagic.GTP3);
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
	 * Close the socket
	 */
	close(code: number = 0, reason: string = "") {
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
	 * Receive a new frame
	 */
	private receive(buf: ArrayBuffer) {
		const frame: BufferStream = new BufferStream(buf);
		const frame_type: FrameType = frame.readUint8();

		switch (frame_type) {
			case FrameType.HELLO: return this.receiveHello(frame);
			case FrameType.RESUME: return this.receiveResume(frame);
			case FrameType.ACK: return this.receiveAck(frame);
			case FrameType.BYE: return this.receiveBye(frame);

			case FrameType.PING: return this.receivePing();
			case FrameType.HEARTBEAT: return this.receiveHeartbeat();

			case FrameType.OPEN: return this.receiveOpen(frame);
			case FrameType.OPEN_SUCCESS: return this.receiveOpenSuccess(frame);
			case FrameType.OPEN_FAILURE: return this.receiveOpenFailure(frame);
			case FrameType.WINDOW_ADJUST: return this.receiveWindowAdjust(frame);
			case FrameType.CLOSE: return this.receiveClose(frame);
			case FrameType.UNKNOW: return this.receiveUnknow(frame);
			case FrameType.EOF: return this.receiveEOF(frame);

			case FrameType.DATA: return this.receiveData(frame);
			case FrameType.DATA_EXT: return this.receiveDataExt(frame);

			case FrameType.REQUEST: return this.receiveRequest(frame);
			case FrameType.SUCCESS: return this.receiveSuccess(frame);
			case FrameType.FAILURE: return this.receiveFailure(frame);

			default: return this.protocolError();
		}
	}

	/**
	 * byte    HELLO
	 * uint32  magic_number
	 */
	private receiveHello(frame: BufferStream) {
		const magic_number = frame.readUint32();

		if (this.state != SocketState.Open || magic_number != ProtocolMagic.GTP3)
			return this.protocolError();

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
		this.ack(seq_last);

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
		this.ack(last_received);
	}

	/**
	 * byte    BYE
	 * int16   code
	 * string  message
	 */
	private receiveBye(frame: BufferStream) {
		const code = frame.readInt16();
		throw new Error("Unimplemented");
	}

	/**
	 * byte    PING
	 */
	private receivePing() {
		// Reply with HEARTBEAT
		this.encoder.sendHeartbeat();
	}

	/**
	 * byte    HEARTBEAT
	 */
	private receiveHeartbeat() {
		// Remote peer is alive, yeah!
	}

	/**
	 * byte    OPEN
	 * string  channel_type
	 * uint16  sender_channel
	 * uint32  window_size
	 * ...     user_data
	 */
	private receiveOpen(frame: BufferStream) {
		const channel_type = frame.readString8();
		const sender_channel = frame.readUint16();
		const window_size = frame.readUint32();

		const id = this.allocateChannelID();
		if (id < 0) {
			this.encoder.sendOpenFailure(sender_channel, 2, "Unnable to allocate channel ID");
			return;
		}

		const channel = new Channel(this, ChannelState.Initialize, id, sender_channel, channel_type, window_size, frame);
		this.channels.set(id, channel);

		if (this.open_handlers.has(channel_type)) {
			this.emit("channel-open-managed", channel);
			try {
				this.open_handlers.get(channel_type)(channel);
			} catch (e) {
				console.error("Channel creation failed", e);
				if (channel.state == ChannelState.Initialize) {
					channel.reject(1, `Channel initialization impossible`);
				} else {
					channel.close();
				}
			}
		} else {
			this.emit("channel-open", channel);
		}
	}

	/**
	 * byte    OPEN_SUCCESS
	 * uint16  recipient_channel
	 * uint16  sender_channel
	 * uint32  window_size
	 * ...     user_data
	 */
	private receiveOpenSuccess(frame: BufferStream) {
		const recipient_channel = frame.readUint16();
		const sender_channel = frame.readUint16();
		const window_size = frame.readUint32();

		if (!this.channels.has(recipient_channel)) {
			return this.encoder.sendUnknow(recipient_channel);
		}

		const channel = this.channels.get(recipient_channel);

		throw new Error("Unimplemented");
	}

	/**
	 * byte    OPEN_FAILURE
	 * uint16  recipient_channel
	 * uint16  sender_channel
	 * int16   code
	 * string  message
	 * ...     user_data
	 */
	private receiveOpenFailure(frame: BufferStream) {
		const recipient_channel = frame.readUint16();
		const sender_channel = frame.readUint16();
		const code = frame.readInt16();
		const message = frame.readString16();

		if (!this.channels.has(recipient_channel)) {
			return this.encoder.sendUnknow(recipient_channel);
		}

		const channel = this.channels.get(recipient_channel);

		throw new Error("Unimplemented");
	}

	/**
	 * byte    WINDOW_ADJUST
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

		throw new Error("Unimplemented");
	}

	/**
	 * byte    CLOSE
	 * uint16  recipient_channel
	 */
	private receiveClose(frame: BufferStream) {
		const recipient_channel = frame.readUint16();

		if (!this.channels.has(recipient_channel)) {
			return this.encoder.sendUnknow(recipient_channel);
		}

		const channel = this.channels.get(recipient_channel);

		throw new Error("Unimplemented");
	}

	/**
	 * byte    UNKNOW
	 * uint16  recipient_channel
	 */
	private receiveUnknow(frame: BufferStream) {
		const recipient_channel = frame.readUint16();

		if (!this.channels.has(recipient_channel)) {
			return this.encoder.sendUnknow(recipient_channel);
		}

		const channel = this.channels.get(recipient_channel);

		throw new Error("Unimplemented");
	}

	/**
	 * byte    EOF
	 * uint16  recipient_channel
	 */
	private receiveEOF(frame: BufferStream) {
		const recipient_channel = frame.readUint16();

		if (!this.channels.has(recipient_channel)) {
			return this.encoder.sendUnknow(recipient_channel);
		}

		const channel = this.channels.get(recipient_channel);

		throw new Error("Unimplemented");
	}

	/**
	 * byte    DATA
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
	 * uint16  recipient_channel
	 * string  request
	 * ...     payload
	 */
	private receiveRequest(frame: BufferStream) {
		const recipient_channel = frame.readUint16();
		const request = frame.readString16();

		if (!this.channels.has(recipient_channel)) {
			return this.encoder.sendUnknow(recipient_channel);
		}

		const channel = this.channels.get(recipient_channel);

		throw new Error("Unimplemented");
	}

	/**
	 * byte    SUCCESS
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
	 * uint16  recipient_channel
	 * int16   code
	 * string  message
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

	/**
	 * Handle acknowledgment processing
	 * @param seq The sequence number being acknowledged
	 */
	private ack(seq: number) {
		// Dequeue while the frame sequence id is less or equal to the acknowledged one
		// Also dequeue if the frame is simply greater than the last acknowledgment, this handle
		// the wrap-around cases
		this.out_buffer.dequeueWhile(f => f.seq_id <= seq || f.seq_id > this.out_ack);
		this.out_ack = seq;
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
}

/**
 * Output encoder helper
 */
class SocketEncoder {
	constructor(private sock: Socket) {}

	/**
	 * Heartbeat message
	 */
	sendHeartbeat() {
		const frame = new BufferStream(1);
		frame.writeUint8(FrameType.HEARTBEAT);
		this.sock.ws.send(frame.buffer());
	}

	/**
	 * Failure to open a channel
	 */
	sendOpenFailure(channel: number, code: number, reason: string, data: ArrayBuffer = null) {
		const frame = new BufferStream(7 + reason.length * 2 + data.byteLength);
		frame.writeUint8(FrameType.OPEN_FAILURE);
		frame.writeUint16(channel);
		frame.writeInt16(code);
		frame.writeString16(reason);
		if (data) frame.writeBuffer(data);
		this.sock.ws.send(frame.compact());
	}

	/**
	 * Unknown channel
	 */
	sendUnknow(channel: number) {
		const frame = new BufferStream(2);
		frame.writeUint8(FrameType.UNKNOW);
		frame.writeUint16(channel);
		this.sock.ws.send(frame.buffer());
	}
}

/************** CHANNEL ******************************************************/

const enum ChannelMode { Stream, Discrete }

const enum ChannelState { Pending, Initialize, Open, Closed }

class Channel extends EventEmitter {
	public state: ChannelState = ChannelState.Pending;

	constructor(
		public socket: Socket,
		public state: ChannelState,
		public id: number,
		public remoteId: number,
		public channel_type: string,
		public window: number,
		public open_data: BufferStream) {
	}

	accept() {}
	reject(code: number, reason: string) {}

	close() {}
}


export = Socket;
