import { deflate, inflate } from "pako";

import { EventEmitter } from "utils/eventemitter";
import { Deferred } from "utils/deferred";
import { NumberPool } from "gtp3/numberpool";
import { FrameType, Protocol } from "gtp3/protocol";
import { Socket, ChannelRequest } from "gtp3/socket";
import { Stream } from "gtp3/stream";
import { UTF8Encoder, UTF8Decoder } from "gtp3/codecs";
import { Frame, MessageFrame, RequestFrame, SuccessFrame, FailureFrame, CloseFrame } from "gtp3/frames";

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
 * The common interface for frames with payload
 */
interface PayloadFrame {
	flags: number;
	payload: ArrayBuffer;
}

/**
 * A shared zero-byte array buffer
 */
const EmptyBuffer = new ArrayBuffer(0);

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

	constructor(public socket: Socket,
	            public local_id: number,
	            public remote_id: number) {
		super();
	}

	/**
	 * Send a message without expecting a reply
	 */
	send(message: string, data: any = null, initial_flags: number = this.default_flags): void {
		// Encode payload
		let [payload, flags] = this.encodePayload(data, initial_flags);

		// Build frame
		const frame = Frame.encode(MessageFrame, 0 , this.remote_id, message, flags, payload);
		this.socket._send(frame, true);
	}

	/**
	 * Send a request and expect a reply
	 */
	request<T>(request: string, data: any = null, initial_flags: number = this.default_flags): Promise<T> {
		// Allocate request ID
		const id = this.requestid_pool.allocate();

		// Encode payload
		let [payload, flags] = this.encodePayload(data, initial_flags);

		// Build frame
		const frame = Frame.encode(RequestFrame, 0 , this.remote_id, request, id, flags, payload);
		this.socket._send(frame, true);

		// Create deferred
		const deferred = new Deferred<T>();
		this.requests.set(id, deferred);
		return deferred.promise;
	}

	/**
	 * Open a new sub-channel
	 */
	openChannel(channel_type: string, token: string = ""): Promise<Channel> {
		return this.socket.openChannel(channel_type, token, this.remote_id);
	}

	/**
	 * Open a new byte stream on a sub-channel
	 */
	/*openStream(stream_type: string, token: string = ""): Promise<Stream> {
		return this.socket.openStream(stream_type, token, this.remote_id);
	}*/

	/**
	 * Close the channel and attempt to flush output buffer
	 */
	close(code: number = 0, reason: string = "Channel closed"): void {
		// Ensure we don't close the channel multiple times
		if (this.state == ChannelState.Closed) return;
		this.state = ChannelState.Closed;

		// Send close message to remote and local listeners
		this.socket._send(Frame.encode(CloseFrame, 0, this.remote_id, code, reason), true);
		this.emit("closed", code, reason);

		this.socket._channelClosed(this);
	}

	/**
	 * Receive a channel specific frame
	 */
	_receive(frame: any): void {
		switch (frame.frame_type) {
			case FrameType.MESSAGE:
			case FrameType.REQUEST:
				return this.receiveMessage(frame);

			case FrameType.SUCCESS:
				return this.receiveSuccess(frame);

			case FrameType.FAILURE:
				return this.receiveFailure(frame);

			case FrameType.CLOSE:
				return this.close(frame.code, frame.reason);
		}
	}

	private receiveMessage(frame: MessageFrame | RequestFrame): void {
		const req_id = (frame instanceof RequestFrame) ? frame.request : 0;
		const payload = this.decodePayload(frame);

		if (frame instanceof RequestFrame) {
			const results: any[] = this.emit("request", frame.message, payload);
			if (results.length > 1) throw new Error("`request` listeners cannot return more than one value");
			const result = results[0] !== undefined ? results[0] : null;

			// Check if we've got a promise object
			if (result && typeof result.then == "function") {
				(<PromiseLike<any>> result).then(
					res => this.sendSuccess(req_id, res),
					err => this.sendFailure(req_id, err.message)
				);
			} else {
				this.sendSuccess(req_id, result);
			}
		} else {
			this.emit("message", frame.message, payload);
		}
	}

	private sendSuccess(request: number, data: any) {
		let [payload, flags] = this.encodePayload(data);
		const frame = Frame.encode(SuccessFrame, 0, this.remote_id, request, flags, payload);
		this.socket._send(frame);
	}

	private sendFailure(request: number, error: string) {
		const frame = Frame.encode(FailureFrame, 0, this.remote_id, request, 0, error);
		this.socket._send(frame);
	}

	private receiveSuccess(frame: SuccessFrame): void {
		// Fetch deferred
		const deferred = this.requests.get(frame.request);
		if (!deferred) return;

		// Successful resolved
		deferred.resolve(this.decodePayload(frame));
	}

	private receiveFailure(frame: FailureFrame): void {
		// Fetch deferred
		const deferred = this.requests.get(frame.request);
		if (!deferred) return;

		// Failure
		const error: any = new Error(frame.message);
		error.code = frame.code;
		deferred.reject(error);
	}

	/**
	 * Decode the frame payload data
	 */
	private decodePayload(frame: PayloadFrame): any {
		const flags = frame.flags;

		if (flags & PayloadFlags.IGNORE) {
			return null;
		}

		let payload: any = frame.payload;

		// Inflate compressed payload
		if (flags & PayloadFlags.COMPRESS) {
			payload = inflate(payload);
		}

		// Decode UTF-8 data
		if (flags & PayloadFlags.UTF8DATA) {
			payload = UTF8Decoder.decode(payload);
		}

		// Decode JSON data
		if (flags & PayloadFlags.JSONDATA) {
			payload = JSON.parse(payload);
		}

		return payload;
	}

	/**
	 * Encode payload data and flags
	 */
	private encodePayload(data: any, flags: number = this.default_flags): [ArrayBuffer, number] {
		if (data && (data.buffer || data) instanceof ArrayBuffer) {
			// Raw buffer
			data = data.buffer || data;
		} else if (typeof data === "string") {
			// String
			data = UTF8Encoder.encode(data).buffer;
			flags |= PayloadFlags.UTF8DATA;
		} else if (data !== null && data !== void 0) {
			// Any other type will simply be JSON-encoded
			data = UTF8Encoder.encode(JSON.stringify(data)).buffer;
			flags |= PayloadFlags.JSONDATA;
		}

		if (!data) {
			// No useful data
			return [EmptyBuffer, PayloadFlags.IGNORE];
		}

		if (flags & PayloadFlags.COMPRESS) {
			if (data.byteLength < Protocol.CompressLimit) {
				flags &= ~PayloadFlags.COMPRESS;
			} else {
				// Deflate payload
				data = deflate(data);
			}
		}

		return [data, flags];
	}

	/**
	 * Tranform a message-based channel to a data-stream channel
	 */
	toStream(): Stream {
		return new Stream(this);
	}

	/**
	 * Called by the Socket when Reset occur
	 */
	_reset(): void {
		this.emit("reset");
		this.emit("closed", 0, "Channel reset");
		this.state = ChannelState.Closed;
	}

	/**
	 * Sub-channel open request emitter
	 */
	_openRequest(request: ChannelRequest): void {
		try {
			this.emit("channel-request", request);
		} catch (e) {
			console.error(e);
			request.reject(1, "Channel initialization impossible");
		}
	}

	/**
	 * Emit the Pause event when buffer grow
	 */
	_pause(buffer_size: number): void {
		this.emit("pause", buffer_size);
	}
}
