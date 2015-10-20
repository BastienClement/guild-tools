import { Queue } from "utils/queue";
import { Deferred } from "utils/deferred";
import { EventEmitter } from "utils/eventemitter";

import { Channel } from "gtp3/channel";
import { NumberPool } from "gtp3/numberpool";
import { UInt64 } from "gtp3/bufferstream";
import { FrameType, Protocol } from "gtp3/protocol";

import { Frame, HelloFrame, ResumeFrame, HandshakeFrame, SyncFrame, AckFrame, PingFrame, PongFrame,
	RequestAckFrame, OpenFrame, OpenSuccessFrame, OpenFailureFrame, ResetFrame } from "gtp3/frames";

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
export interface ChannelRequest {
	channel_type: string;
	token: string;
	channel?: Channel;
	accept(): Channel;
	reject(code?: number, reason?: string): void;
	replied(): boolean;
}

/**
 * The socket delegate
 */
export interface SocketDelegate {
	connected(version: string): void;
	reconnecting(): void;
	disconnected(code: number, reason: string): void;
	reset(): void;
	updateLatency(latency: number): void;
	openChannel(request: ChannelRequest): void;
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
	private channels_pending: Map<number, Deferred<number>> = new Map<number, Deferred<number>>();
	private channelid_pool: NumberPool = new NumberPool(Protocol.ChannelsLimit);

	// Last received frame sequence number
	private in_seq: number = 0;

	// Output queue and sequence numbers
	private out_seq: number = 0;
	private out_ack: number = 0;
	private out_buffer: Queue<SequencedFrame> = new Queue<SequencedFrame>();

	// Reconnect attempts counter
	private retry_count: number = 0;

	// Limit the number of REQUEST_ACK commands
	private request_ack_cooldown: number = 0;
	private paused: boolean = false;

	// Last ping time
	private ping_time: number = 0;
	public latency: number = 0;

	// Tracing mode
	public verbose: Boolean = false;

	/**
	 * Constructor
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
		let ws = this.ws = new WebSocket(this.url, "GTP3-WS");
		ws.binaryType = "arraybuffer";

		// Reconnect on error or socket closed
		let closed_once = false;
		ws.onerror = ws.onclose = () => {
			if (closed_once) return;
			closed_once = true;
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
				frame = Frame.encode(HelloFrame, Protocol.GTP3, `GuildTools Client [${navigator.userAgent}]`);
				break;

			case SocketState.Reconnecting: // Resume an already open socket
				frame = Frame.encode(ResumeFrame, this.id, this.in_seq);
				break;

			default: // Handshake cannot be called from this state
				throw new Error(`Cannot generate handshake from state '${this.state}'`);
		}

		this.state = SocketState.Open;
		this.retry_count = 0;

		if (this.verbose) this.trace(">>", Frame.decode(frame));
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
			this.emit("reconnect");
		}

		// Check retry count and current state
		if (this.state != SocketState.Reconnecting || this.retry_count > 5) {
			this.close();
			return;
		}

		// Exponential backoff timer between reconnects
		this.ws = null;
		const backoff_time = Math.pow(2, this.retry_count++) * 500;
		setTimeout(() => this.connect(), backoff_time);
	}

	/**
	 * Close the socket
	 */
	close(): void {
		// Ensure the socket is not closed more than once
		if (this.state == SocketState.Closed) return;
		this.state = SocketState.Closed;

		// Emit event
		this.emit("disconnect");

		// Actually close the WebSocket
		this.ws.close();
		this.ws = null;

		// Close open channels
		this.channels.forEach(chan => chan.close(-1, "Socket closed"));
	}

	/**
	 * Put the socket back in ready state
	 */
	private ready(version: string = null): void {
		this.state = SocketState.Ready;
		this.emit("connected", version);
	}

	/**
	 * Send a PING message and compute RTT latency
	 */
	ping(): void {
		this.ping_time = performance.now();
		this._send(Frame.encode(PingFrame));
	}

	/**
	 * Open a new channel on this socket
	 */
	openChannel(channel_type: string, token: string = "", parent: number = 0): Promise<Channel> {
		const id = this.channelid_pool.allocate();
		const deferred = new Deferred<number>();
		this.channels_pending.set(id, deferred);

		// Timeout for server to send OPEN_SUCCESS or OPEN_FAILURE
		setTimeout(() => deferred.reject(new Error("Timeout")), Protocol.OpenTimeout);

		// Send the open message to the server
		this._send(Frame.encode(OpenFrame, 0, id, channel_type, token, parent), true);

		// Create the channel once the remote_id is received
		const promise = deferred.promise.then(remote_id => {
			const channel = new Channel(this, channel_type, id, remote_id);
			this.channels_pending.delete(id);
			this.channels.set(id, channel);
			return channel;
		});

		// Release channel ID if open fail
		promise.then(null, () => this.channelid_pool.release(id));

		return promise;
	}

	/**
	 * Open a new byte stream on this socket
	 */
	/*openStream(stream_type: string, token: string = "", parent: number = 0): Promise<Stream> {
		return this.openChannel(stream_type, null, token, parent).then(channel => new Stream(channel));
	}*/

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
	 * Receive a new frame, dispatch
	 */
	private receive(buf: ArrayBuffer): void {
		const frame: any = Frame.decode(buf);
		if (this.verbose) this.trace("<<", frame);

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

			case FrameType.IGNORE:
				return;

			case FrameType.PING:
				return this._send(Frame.encode(PingFrame));

			case FrameType.PONG:
				this.latency = performance.now() - this.ping_time;
				this.emit("update-latency", this.latency);
				return;

			case FrameType.REQUEST_ACK:
				return this._send(Frame.encode(AckFrame, this.in_seq));

			case FrameType.OPEN:
				return this.receiveOpen(frame);

			case FrameType.OPEN_SUCCESS:
				return this.receiveOpenSuccess(frame);

			case FrameType.OPEN_FAILURE:
				return this.receiveOpenFailure(frame);

			case FrameType.RESET:
				return this.receiveReset(frame);

			default:
				this.protocolError();
		}
	}

	/**
	 * Handshake message, socket is ready
	 */
	private receiveHandshake(frame: HandshakeFrame): void {
		if (this.state != SocketState.Open || frame.magic != Protocol.GTP3)
			return this.protocolError();
        
		if (this.id !== UInt64.Zero) this.reset();
		this.id = frame.sockid;

		this.ready(frame.version);
	}

	/**
	 * Resync the socket with the server
	 */
	private receiveSync(frame: SyncFrame): void {
		if (this.state != SocketState.Open)
			return this.protocolError();

		// Treat the Sync message as an acknowledgment
		// This will remove any queued frames not acknowledged but in fact received
		// SyncFrame is compatible with AckFrame
		this.receiveAck(frame);

		this.ready();

		// Now, what's left in the buffer is only unreceived frame
		this.out_buffer.foreach(f => this._send(f.frame, false));
	}

	/**
	 * Received a ACK from the server, clean outgoing queue
	 */
	private receiveAck(frame: AckFrame): void {
		const seq = frame.last_seq;

		// Dequeue while the frame sequence id is less or equal to the acknowledged one
		// Also dequeue if the frame is simply greater than the last acknowledgment, this handle
		// the wrap-around case
		while (!this.out_buffer.empty()) {
			const f = this.out_buffer.peek();
			if (f.seq <= seq || (f.seq > this.out_ack && seq < this.out_ack)) {
				this.out_buffer.dequeue();
			} else {
				break;
			}
		}
		
		// Check if we can emit a resume event
		const out_buffer_len = this.out_buffer.length();
		
		if (this.paused && out_buffer_len < Protocol.BufferPauseLimit) {
			this.channels.forEach(chan => chan._receive(out_buffer_len));
			this.paused = false;
		}

		// Save the sequence number as the last one received
		this.out_ack = seq;
	}

	/**
	 * Channel open request
	 */
	private receiveOpen(frame: OpenFrame): void {
		const sender_channel = frame.sender_channel;
		let request_replied = false;

		let request: ChannelRequest;
		request = {
			// The requested channel type
			channel_type: frame.channel_type,

			// The open token
			token: frame.token,

			// The channel, once the request is accepted
			channel: null,

			// Accept the open request
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
				const channel = new Channel(this, frame.channel_type, id, sender_channel);

				request_replied = true;
				this.channels.set(id, channel);
				this._send(Frame.encode(OpenSuccessFrame, 0, sender_channel, channel), true);

				return request.channel = channel;
			},

			// Reject the open request
			reject: (code: number, reason: string) => {
				if (request_replied) return;
				request_replied = true;
				this._send(Frame.encode(OpenFailureFrame, 0, sender_channel, code, reason), true);
			},

			replied: () => {
				return request_replied;
			}
		};

		if (frame.parent_channel != 0) {
			const channel = this.channels.get(frame.parent_channel);
			if (!channel) {
				console.error("Undefined parent channel");
				request.reject(2, "Undefined parent channel");
			}
			channel._openRequest(request);
		} else {
			try {
				this.emit("channel-request", request);
			} catch (e) {
				console.error(e);
				request.reject(1, "Channel initialization impossible");
			}
		}
	}

	/**
	 * Channel successfully open
	 */
	private receiveOpenSuccess(frame: OpenSuccessFrame): void {
		const channel_id = frame.recipient_channel;

		const deferred = this.channels_pending.get(channel_id);
		if (!deferred) return this._send(Frame.encode(ResetFrame, channel_id));
		this.channels_pending.delete(channel_id);

		deferred.resolve(frame.sender_channel);
	}

	/**
	 * Failure to open a channel
	 */
	private receiveOpenFailure(frame: OpenFailureFrame): void {
		const channel_id = frame.recipient_channel;

		const deferred = this.channels_pending.get(channel_id);
		if (!deferred) return this._send(Frame.encode(ResetFrame, channel_id));
		this.channels_pending.delete(channel_id);

		const error: any = new Error(frame.message);
		error.code = frame.code;
		deferred.reject(error);
	}

	/**
	 * Destroy a server-side undefined channel
	 */
	private receiveReset(frame: ResetFrame): void {
		// Close any channel matching the DESTROY message
		this.channels.forEach(channel => {
			if (channel.remote_id == frame.sender_channel) channel._reset();
		});
	}

	/**
	 * Generic channel frame handler
	 */
	private receiveChannelFrame(frame: Frame): void {
		const channel = this.channels.get(frame.channel);
		if (!channel) return this._send(Frame.encode(ResetFrame, frame.channel));
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
	 * Send a complete frame
	 */
	_send(frame: ArrayBuffer, seq?: boolean): void {
		// Ensure a maximum frame size
		if (frame.byteLength > Protocol.FrameLimit) {
			throw new Error("Frame size limit exceeded");
		}

		// Add the sequence number to the frame if requested
		if (seq) {
			// Check the size of the output buffer
			const out_buffer_len = this.out_buffer.length();
			if (out_buffer_len >= Protocol.BufferHardLimit) {
				throw new Error("Output buffer is full");
			} else if (out_buffer_len >= Protocol.BufferSoftLimit) {
				if (--this.request_ack_cooldown <= 0) {
					this._send(Frame.encode(RequestAckFrame));
					this.request_ack_cooldown = Protocol.RequestAckCooldown;
					if (out_buffer_len >= Protocol.BufferPauseLimit) {
						this.channels.forEach(chan => chan._pause(out_buffer_len));
						this.paused = true;
					}
				}
			}

			// Compute the next sequence number
			this.out_seq = (this.out_seq + 1) & 0xFFFF;

			// Tag the frame
			const bytes = new Uint8Array(frame, 1, 2);
			bytes[0] = this.out_seq >> 8 & 0xFF;
			bytes[1] = this.out_seq & 0xFF;

			// Push the frame in the output buffer for later replay
			this.out_buffer.enqueue({frame: frame, seq: this.out_seq});
		}

		if (this.state == SocketState.Ready) {
			if (this.verbose) this.trace(">>", Frame.decode(frame));
			this.ws.send(frame);
		}
	}

	/**
	 * Called by channels when closed
	 */
	_channelClosed(channel: Channel) {
		const id = channel.local_id;
		this.channels.delete(id);
		this.channelid_pool.release(id);
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
		this.emit("reset");
	}

	/**
	 * Print the socket activity
	 */
	private trace(direction: string, frame: any) {
		let frame_name = frame.frame_name;
		let padding = " ".repeat(15 - frame_name.length);
		console.debug(direction, frame_name + padding, frame);
	}
}
