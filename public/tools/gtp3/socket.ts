import { EventEmitter } from "utils/eventemitter";
import { Queue } from "utils/queue";
import { Deferred } from "utils/deferred";

import { Channel } from "gtp3/channel";
import { Stream } from "gtp3/stream";
import { NumberPool } from "gtp3/numberpool";
import { UInt64 } from "gtp3/bufferstream";
import { FrameType, Protocol, CommandCode } from "gtp3/protocol";
import { UTF8Encoder, UTF8Decoder } from "gtp3/codecs";

import { Frame, HelloFrame, ResumeFrame, HandshakeFrame, SyncFrame, AckFrame, ByeFrame, CommandFrame,
	OpenFrame, OpenSuccessFrame, OpenFailureFrame, DestroyFrame } from "gtp3/frames";

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
	private id: UInt64 = UInt64.Zero;

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
		let frame: ArrayBuffer;

		switch (this.state) {
			case SocketState.Uninitialized: // Create a new socket
				frame = Frame.encode(HelloFrame, Protocol.GTP3, `GuildTools [${navigator.userAgent}]`);
				break;

			case SocketState.Reconnecting: // Resume an already open socket
				frame = Frame.encode(ResumeFrame, this.id, this.in_seq);
				break;

			default: // Handshake cannot be called from this state
				throw new Error(`Cannot generate handshake from state '${this.state}'`);
		}

		this.state = SocketState.Open;
		this.retry_count = 0;
		this.emit("open");

		this.ws.send(frame);
	}

	/**
	 * Handle reconnection logic
	 */
	private reconnect(): void {
		// Check if socket is already closed
		if (this.state === SocketState.Closed) {
			return;
		}

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
	close(code: number = 3000, reason: string = ""): void {
		// Ensure the socket is not closed more than once
		if (this.state == SocketState.Closed) return;
		this.state = SocketState.Closed;

		// Emit events and messages
		this.emit("closed");
		this._send(Frame.encode(ByeFrame, code, reason));

		// Actually close the WebSocket
		this.ws.close(code, reason);
		this.ws = null;
	}

	/**
	 * Put the socket back in ready state
	 */
	private ready(version: string = null): void {
		this.state = SocketState.Ready;
		this.emit("ready", version);
	}

	/**
	 * Send a PING message and compute RTT latency
	 */
	ping(): void {
		this.ping_time = Date.now();
		this.sendCommand(CommandCode.PING);
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
		this._send(Frame.encode(OpenFrame, 0, id, channel_type, token, parent), true);

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
	private sendAck(seq: number): void {
		// Ensure the frame was not already received
		if (seq <= this.in_seq && (seq != 0 || this.in_seq == 0)) {
			throw DuplicatedFrame;
		}

		// Store the sequence number as the last received one
		this.in_seq = seq;

		// Only send an actual ACK if multiple of AckInterval
		if (seq % Protocol.AckInterval == 0) {
			this._send(Frame.encode(AckFrame, seq));
		}
	}

	/**
	 * Receive a new frame
	 */
	private receive(buf: ArrayBuffer): void {
		const frame: any = Frame.decode(buf);

		if (frame.sequenced) {
			this.sendAck(frame.seq);
		}

		if (frame.channel_frame) {
			return this.receiveChannelFrame(frame);
		}

		switch (frame.frame_type) {
			case FrameType.HANDSHAKE:
				return this.receiveHandshake(frame);

			case FrameType.SYNC:
				return this.receiveSync(frame);

			case FrameType.ACK:
				return this.receiveAck(frame);

			case FrameType.BYE:
				return this.close(frame.code, frame.message);

			case FrameType.IGNORE:
				return;

			case FrameType.COMMAND:
				return this.receiveCommand(frame);

			case FrameType.OPEN:
				return this.receiveOpen(frame);

			case FrameType.OPEN_SUCCESS:
				return this.receiveOpenSuccess(frame);

			case FrameType.OPEN_FAILURE:
				return this.receiveOpenFailure(frame);

			case FrameType.DESTROY:
				return this.receiveDestroy(frame);

			default:
				this.protocolError();
		}
	}

	private receiveHandshake(frame: HandshakeFrame): void {
		if (this.state != SocketState.Open || frame.magic != Protocol.GTP3)
			return this.protocolError();

		if (this.id !== UInt64.Zero) this.reset();
		this.id = frame.sockid;

		this.ready(frame.version);
	}

	private receiveSync(frame: SyncFrame): void {
		if (this.state != SocketState.Open)
			return this.protocolError();

		// Treat the Sync message as an acknowledgment
		// This will remove any queued frames not acknowledged but in fact received
		// SyncFrame is compatible with AckFrame
		this.receiveAck(frame);

		// Now, what's left in the buffer is only unreceived frame
		this.out_buffer.foreach(f => this.ws.send(f));

		this.ready();
	}

	private receiveAck(frame: AckFrame): void {
		const seq = frame.last_seq;
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

	private receiveCommand(frame: CommandFrame): void {
		switch (frame.command) {
			case CommandCode.PING:
				return this.sendCommand(CommandCode.PONG);

			case CommandCode.PONG:
				this.latency = Date.now() - this.ping_time;
				this.emit("latency", this.latency);
				return;

			case CommandCode.REQUEST_ACK:
				return this._send(Frame.encode(AckFrame, this.in_seq));
		}
	}

	private receiveOpen(frame: OpenFrame): void {
		const sender_channel = frame.sender_channel;
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
				this._send(Frame.encode(OpenSuccessFrame, 0, sender_channel, channel), true);

				// Release the channel ID on close
				channel.on("closed", () => this.channelid_pool.release(id));

				return channel;
			},

			reject: (code: number, reason: string) => {
				if (request_replied) return;
				request_replied = true;
				this._send(Frame.encode(OpenFailureFrame, 0, sender_channel, code, reason), true);
			}
		};

		if (this.open_handlers.has(frame.channel_type)) {
			this.emit("channel-open-managed", request);
			try {
				this.open_handlers.get(frame.channel_type)(request, frame.token);
				if (!request_replied) throw null;
			} catch (e) {
				console.error("Channel creation failed", e);
				request.reject(1, "Channel initialization impossible");
			}
		} else {
			this.emit("channel-open", frame.channel_type, request);
		}
	}

	private receiveOpenSuccess(frame: OpenSuccessFrame): void {
		const channel_id = frame.recipient_channel;

		const deferred = this.channels_pending.get(channel_id);
		if (!deferred) return this._send(Frame.encode(DestroyFrame, channel_id));
		this.channels_pending.delete(channel_id);

		const channel = new Channel(this, channel_id, frame.sender_channel);
		deferred.resolve(channel);
	}

	private receiveOpenFailure(frame: OpenFailureFrame): void {
		const channel_id = frame.recipient_channel;

		const deferred = this.channels_pending.get(channel_id);
		if (!deferred) return this._send(Frame.encode(DestroyFrame, channel_id));
		this.channels_pending.delete(channel_id);

		const error: any = new Error(frame.message);
		error.code = frame.code;
		deferred.reject(error);
	}

	private receiveDestroy(frame: DestroyFrame): void {
		// Close any channel matching the DESTROY message
		this.channels.forEach(channel => {
			if (channel.remote_id == frame.sender_channel) channel.close(-1, "Socket destroyed");
		});
	}

	/**
	 * Generic channel frame handler
	 */
	private receiveChannelFrame(frame: Frame): void {
		const channel = this.channels.get(frame.channel);
		if (!channel) return this._send(Frame.encode(DestroyFrame, frame.channel));
		channel._receive(frame);
	}

	/**
	 * Handle force closing the socket due to protocol error
	 */
	private protocolError(): void {
		this.close();
		throw new Error("Protocol error");
	}

	/**
	 * Helper function for all simple frames
	 */
	private sendCommand(command: number): void {
		this._send(Frame.encode(CommandFrame, command));
	}

	/**
	 * Send a complete frame
	 */
	_send(frame: ArrayBuffer, seq: boolean = false): void {
		// Ensure a maximum frame size
		if (frame.byteLength > Protocol.FrameLimit) {
			throw new Error("Frame size limit exceeded");
		}

		// Add the sequence number to the frame if requested
		if (seq) {
			// Check the size of the output buffer
			const out_buffer_len = this.out_buffer.length();
			if (out_buffer_len > Protocol.BufferHardLimit) {
				throw new Error("Output buffer is full");
			} else if (out_buffer_len > Protocol.BufferSoftLimit) {
				this.sendCommand(CommandCode.REQUEST_ACK);
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

	/**
	 * Reconnected to the server, but unable to restore context
	 */
	private reset() {
		// Send reset on channels
		this.channels.forEach(c => c._reset());

		// Clear own state
		this.channels.clear();
		this.channels_pending.clear();
		this.channelid_pool.clear();
		this.in_seq = 0;
		this.out_seq = 0;
		this.out_ack = 0;
		this.out_buffer.clear();

		// Emit reset event
		this.emit("resync");
	}
}
