import { deflate, inflate } from "pako";

import { EventEmitter } from "utils/eventemitter";
import { Deferred } from "utils/deferred";
import { NumberPool } from "gtp3/numberpool";
import { FrameType, Protocol, CommandCode } from "gtp3/protocol";
import { Socket } from "gtp3/socket";
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
 * An object used to handle messages and requests
 */
interface ChannelDelegate {
	[key: string]: Function;
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

	// The object used to handle channel messages and requests
	private delegate: ChannelDelegate = null;

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
	 * Register this channel's delegate
	 */
	registerDelegate(delegate: ChannelDelegate) {
		this.delegate = delegate;
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
		const frame = Frame.encode(MessageFrame, 0 , this.remote_id, request, id, flags, payload);
		this.socket._send(frame, true);

		// Create deferred
		const deferred = new Deferred<T>();
		this.requests.set(id, deferred);
		return deferred.promise;
	}

	/**
	 * Close the channel and attempt to flush output buffer
	 */
	close(code: number, reason: string): void {
		// Ensure we don't close the channel multiple times
		if (this.state == ChannelState.Closed) return;
		this.state = ChannelState.Closed;

		// Send close message to remote and local listeners
		this.socket._send(Frame.encode(CloseFrame, 0, code, reason), true);
		this.emit("closed");
	}

	/**
	 * Receive a channel specific frame
	 */
	_receive(frame: any): void {
		switch (frame.frame_type) {
			case FrameType.MESSAGE:
				return this.receiveMessage(frame, false);

			case FrameType.REQUEST:
				return this.receiveMessage(frame, true);

			case FrameType.SUCCESS:
				return this.receiveSuccess(frame);

			case FrameType.FAILURE:
				return this.receiveFailure(frame);

			case FrameType.CLOSE:
				return this.close(frame.code, frame.reason);
		}
	}

	private receiveMessage(frame: MessageFrame | RequestFrame, request: boolean): void {
		// Fetch message handler
		const handler = this.delegate && this.delegate[frame.message];
		const req_id = (frame instanceof RequestFrame) ? frame.request : 0;
		if (!handler) {
			if (request) {
				// If it's a request, send a failure frame
				this.sendFailure(req_id, "Undefined request handler");
			}
			return;
		}

		const payload = this.decodePayload(frame);
		try {
			const results = handler(payload);
			if (typeof results.then == "function") {
				(<PromiseLike<any>> results).then(
					res => this.sendSuccess(req_id, res),
					err => this.sendFailure(req_id, err.message)
				);
			} else {
				this.sendSuccess(req_id, results);
			}
		} catch (e) {
			this.sendFailure(req_id, e.message);
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
		this.emit("closed");
		this.state = ChannelState.Closed;
	}
}
