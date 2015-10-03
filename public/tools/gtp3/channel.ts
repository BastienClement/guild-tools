import { EventEmitter } from "utils/eventemitter";
import { Deferred } from "utils/deferred";
import { NumberPool } from "gtp3/numberpool";
import { FrameType, Protocol } from "gtp3/protocol";
import { Socket, ChannelRequest } from "gtp3/socket";
import { Stream } from "gtp3/stream";
import { Frame, MessageFrame, RequestFrame, SuccessFrame, FailureFrame, CloseFrame } from "gtp3/frames";
import { Payload, PayloadFrame } from "gtp3/payload";

/**
 * Channel states
 */
const enum ChannelState { Open, Closed }

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
				public name: string,
	            public local_id: number,
	            public remote_id: number) {
		super();
	}

	/**
	 * Send a message without expecting a reply
	 */
	send(message: string, data: any = null, initial_flags: number = this.default_flags): void {
		// Encode payload
		let [payload, flags] = Payload.encode(data, initial_flags);

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
		let [payload, flags] = Payload.encode(data, initial_flags);

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
		this.emit("close", code, reason);

		this.socket._channelClosed(this);
	}

	/**
	 * Receive a channel specific frame
	 */
	_receive(frame: any): void {
		switch (frame.frame_type) {
			case FrameType.MESSAGE:
			case FrameType.REQUEST:
				this.receiveMessage(frame);
				return;

			case FrameType.SUCCESS:
				return this.receiveSuccess(frame);

			case FrameType.FAILURE:
				return this.receiveFailure(frame);

			case FrameType.CLOSE:
				return this.close(frame.code, frame.reason);
		}
	}

	private async receiveMessage(frame: MessageFrame | RequestFrame) {
		const req_id = (frame instanceof RequestFrame) ? frame.request : 0;
		const payload = Payload.decode(frame);

		if (frame instanceof RequestFrame) {
			const results: any[] = await this.emit("request", frame.message, payload);
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
			if (frame.message == "$error") {
				console.error(new Error(payload));
			} else {
				this.emit("message", frame.message, payload);
			}
		}
	}

	private sendSuccess(request: number, data: any) {
		let [payload, flags] = Payload.encode(data);
		const frame = Frame.encode(SuccessFrame, 0, this.remote_id, request, flags, payload);
		this.socket._send(frame);
	}

	private sendFailure(request: number, error: string) {
		const frame = Frame.encode(FailureFrame, 0, this.remote_id, request, 0, error);
		this.socket._send(frame);
	}
	
	private getRequestDeferred(request_id: number) {
		const deferred = this.requests.get(request_id);
		if (!deferred) return;
		
		this.requests.delete(request_id);
		this.requestid_pool.release(request_id);
		
		return deferred;
	}

	private receiveSuccess(frame: SuccessFrame): void {
		// Fetch deferred
		const deferred = this.getRequestDeferred(frame.request);
		if (!deferred) return;

		// Successful resolved
		deferred.resolve(Payload.decode(frame));
	}

	private receiveFailure(frame: FailureFrame): void {
		// Fetch deferred
		const deferred = this.getRequestDeferred(frame.request);
		if (!deferred) return;

		// Failure
		const error: any = new Error(frame.message);
		error.code = frame.code;
		deferred.reject(error);
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
		this.emit("close", 0, "Channel reset");
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
	
	/**
	 * Emit the Pause event when buffer grow
	 */
	_resume(buffer_size: number): void {
		this.emit("resume", buffer_size);
	}
}
