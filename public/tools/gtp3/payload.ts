import { deflate, inflate } from "pako";
import { Protocol } from "gtp3/protocol";
import { UTF8Encoder, UTF8Decoder } from "gtp3/codecs";

/**
 * Frame flags indicating high-level encoding
 */
const enum PayloadFlags {
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
 * A shared zero-byte array buffer
 */
const EmptyBuffer = new ArrayBuffer(0);

export const Payload = {
	/**
	 * Decode the frame payload data
	 */
	decode(frame: PayloadFrame): any {
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
	},

	/**
	 * Encode payload data and flags
	 */
	encode(data: any, flags: number = this.default_flags): [ArrayBuffer, number] {
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
};
