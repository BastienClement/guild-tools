import {ServiceWorker} from "../utils/Worker";
import {UTF8Decoder, UTF8Encoder} from "./Codecs";
import {Protocol} from "./Protocol";

/**
 * Frame flags indicating high-level encoding
 */
export const enum PayloadFlags {
	COMPRESS = 0x01,
	UTF8DATA = 0x02,
	JSONDATA = 0x04, // Require UTF8DATA
	IGNORE = 0x80
}

/**
 * The common interface for frames with payload
 */
export interface PayloadFrame {
	flags: number;
	payload: ArrayBuffer;
}

/**
 * The return type of the encode function
 */
type PayloadAndFlags = [ArrayBuffer, number];

/**
 * A shared zero-byte array buffer
 */
const EmptyBuffer = new ArrayBuffer(0);

const CompressWorker = new ServiceWorker("/assets/workers/compress.js");

/**
 * Revive special encoded value
 * Currently, only support timestamp to Date.
 */
function reviver(k: string, v: any): any {
	if (typeof v === "object" && v !== null) {
		let keys = Object.keys(v);
		if (keys.length == 1) {
			let k = keys[0];
			switch (k) {
				case "$date":
					return new Date(v[k]);
			}
		}
	}
	return v;
}

/**
 * Payload utilities
 */
export const Payload = {
	/**
	 * Decode the frame payload data
	 */
	async decode(frame: PayloadFrame): Promise<any> {
		const flags = frame.flags;

		if (flags & PayloadFlags.IGNORE) {
			return Promise.resolve(null);
		}

		let payload: any = frame.payload;

		// Inflate compressed payload
		if (flags & PayloadFlags.COMPRESS) {
			payload = await CompressWorker.request<ArrayBuffer>("inflate", payload);
		}

		// Decode UTF-8 data
		if (flags & PayloadFlags.UTF8DATA) {
			payload = UTF8Decoder.decode(payload);
		}

		// Decode JSON data
		if (flags & PayloadFlags.JSONDATA) {
			payload = JSON.parse(payload, reviver);
		}

		return payload;
	},

	/**
	 * Encode payload data and flags
	 */
	async encode(data: any, flags: number = this.default_flags): Promise<PayloadAndFlags> {
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
			flags |= PayloadFlags.JSONDATA | PayloadFlags.UTF8DATA;
		}

		if (!data) {
			// No useful data
			return Promise.resolve<PayloadAndFlags>([EmptyBuffer, PayloadFlags.IGNORE]);
		}

		if (flags & PayloadFlags.COMPRESS) {
			if (data.byteLength < Protocol.CompressLimit) {
				flags &= ~PayloadFlags.COMPRESS;
			} else {
				// Deflate payload
				data = await CompressWorker.request<ArrayBuffer>("deflate", data);
			}
		}

		return Promise.resolve<PayloadAndFlags>([data, flags]);
	}
};
