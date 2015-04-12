/// <reference path="./defs/encoding.d.ts" />
/// <reference path="./defs/pako.d.ts" />

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
	BYE = 0x00,

	// Ping mechanism
	PING = 0xB1,
	HEARTBEAT = 0xBE,

	// Channel control
	OPEN = 0xC0,
	OPEN_SUCCESS = 0xC1,
	OPEN_FAILURE = 0xC2,
	WINDOW_ADJUST = 0xCA,
	CLOSE = 0xCC,
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
	private ws: WebSocket;
	private state: SocketState = SocketState.Uninitialized;

	// Available channels
	private channels = new Map<number, Channel>();

	// Last received frame sequence number
	private in_seq = 0;

	// Output queue and sequence numbers
	private out_seq = 0;
	private out_ack = 0;
	private out_buffer: Queue<SequencedArrayBuffer>;

	// Reconnect attempts counter
	private retry_count = 0;

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
		var frame: BufferStream;

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
	private receivePing(frame: BufferStream) {
		throw new Error("Unimplemented");
	}

	/**
	 * byte    HEARTBEAT
	 */
	private receiveHeartbeat(frame: BufferStream) {
		throw new Error("Unimplemented");
	}

	/**
	 * byte    Open
	 * string  channel_type
	 * uint16  remote_id
	 * uint32  window_size
	 * ...
	 */
	private receiveOpen(frame: BufferStream) {
		throw new Error("Unimplemented");
	}

	/**
	 * Put the socket back in ready state
	 */
	private ready() {
		this.state = SocketState.Ready;
		this.emit("ready");
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

	send(buf: ArrayBuffer, channel: Channel) {
		const frame = new DataView(buf);
		var flags = frame.getUint16(3);

		const seq_id = this.out_seq = (this.out_seq + 1) & 0xFFFF;
		frame.setUint16(1, seq_id);

		if (seq_id == 0) {
			flags |= FrameFlags.SequenceWrap;
		}

		frame.setUint16(3, flags);
	}
}

/************** CHANNEL ******************************************************/

const enum ChannelMode { Stream, Discrete }

interface ChannelConfig {

}

class Channel extends EventEmitter {
	constructor(private sock: Socket, config: ChannelConfig = {}) {
		super();
	}

	send(buf: ArrayBuffer) {
		this.sock.send(buf);
	}

	receive(frame: DataView) {
		const frame_type = frame.getUint8(2);
	}
}


export = Socket;
