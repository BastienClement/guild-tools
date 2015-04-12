/// <reference path="./defs/encoding.d.ts" />
/// <reference path="./defs/pako.d.ts" />

importScripts("/assets/javascripts/pako.js");
importScripts("/assets/javascripts/encodings.js");

interface SocketWorkerRPC {
	_: string;  // Method to call
	$: any[];   // Arguments
}

interface SocketWorkerFrame {

}

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

const enum FrameFlags {
	Compress = 0x0001,
	StringData = 0x0002
}

const encoder = new TextEncoder("utf-8");
const decoder = new TextDecoder("utf-8");

class SocketWorker {
	private frame: ArrayBuffer;

	allocFrame(length: number) {
		this.frame = new ArrayBuffer(length + 5);
	}

	writeHeaders(channel: number, flags: number, sequence: number) {
		const headers = new DataView(this.frame);
		headers.setUint8(0, channel);
		headers.setUint16(1, sequence);
		headers.setUint16(3, flags);
	}

	writeData(data: ArrayBuffer) {
		const payload = new Uint8Array(this.frame, 5);
		payload.set(new Uint8Array(data));
	}

	send(data: ArrayBuffer|string, channel: number, flags: number, sequence: number) {
		var payload: ArrayBuffer;

		if (typeof data === "string") {
			payload = encoder.encode(data).buffer;
			flags |= FrameFlags.StringData;
		} else {
			payload = data;
		}

		if (flags & FrameFlags.Compress) {
			if (payload.byteLength < 250) {
				// Data is too short to be compressed, remove flag and ignore
				flags &= ~FrameFlags.Compress;
			} else {
				payload = pako.deflate(payload);
			}
		}

		this.allocFrame(payload.byteLength);
		this.writeHeaders(channel, flags, sequence);
		this.writeData(payload);

		self.postMessage({ _: "emit", $: this.frame }, <any>[this.frame]);
	}

	// Allow indirect fields access
	[idx: string]: any;
}

const worker = new SocketWorker();

self.onmessage = function handle(msg: MessageEvent) {
	const rpc = <SocketWorkerRPC> msg.data;
	worker[rpc._].apply(worker, rpc.$);
};
