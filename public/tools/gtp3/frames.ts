import * as Codecs from "gtp3/codecs";
import { Codec } from "gtp3/codecs";
import { FrameType } from "gtp3/protocol";
import { BufferReader, BufferWriter, UInt64 } from "gtp3/bufferstream";

/**
 * A codec binding linking an object property key to a specific data codec
 */
interface CodecBinding {
	key: string;
	codec: Codec<any>;
}

/**
 * Interface a Frame-subtype constructor
 */
interface FrameConstructor {
	new(..._: any[]): Frame;
	prototype: Frame;
}

/**
 * Frame encoder / decoder
 */
export class Frame {
	// List of frame constructors for each type code
	static constructors: Map<number, FrameConstructor> = new Map<number, FrameConstructor>();

	// The frame codec bindings
	__codecs: CodecBinding[];

	// The frame type
	frame_type: number;

	// Sequence number
	sequenced: boolean;
	seq: number;

	// Target channel id
	channel_frame: boolean;
	channel: number;

	// Allow direct property access without compiler warning
	[key: string]: any;

	// Construct any frame subtypes based on codecs informations
	constructor(...fields: any[]) {
		if (this.__codecs) {
			for (let i = this.__codecs.length - 1; i >= 0; i--) {
				const binding = this.__codecs[i];
				this[binding.key] = fields[i];
			}
		} else {
			throw new Error("No codecs defined for frame subtype. Unable to build.");
		}
	}

	/**
	 * Decode a buffer into a Frame object
	 */
	static decode(buffer: ArrayBuffer): Frame {
		// Identify the frame type
		const reader = new BufferReader(buffer);
		const ftype = reader.uint8();
		const ctor = Frame.constructors.get(ftype);

		// Ensure we have a known constructor for this frame type
		if (!ctor) {
			throw new Error(`Unknown frame type : ${ftype}`);
		}

		// Extract constructor arguments from codecs
		const codecs = ctor.prototype.__codecs;
		const args: any[] = [];

		for (let i = 0; i < codecs.length; ++i) {
			args.push(codecs[i].codec.decode(reader));
		}

		// Create the frame object
		const obj = Object.create(ctor.prototype);
		ctor.apply(obj, args);

		return obj;
	}

	/**
	 * Encode a frame object into an ArrayBuffer
	 * Can accept built objects or a (Constructor, ...Args) list.
	 */
	static encode(frame: Frame): ArrayBuffer;
	static encode(frame: FrameConstructor, ...data: any[]): ArrayBuffer;
	static encode(frame: any, ...data: any[]): ArrayBuffer {
		// Fetch codecs list and frame type
		const proto = (frame instanceof Frame) ? frame : frame.prototype;
		const codecs: CodecBinding[] = proto.__codecs;
		const frame_type: number = proto.frame_type;

		// Create the data parameter based on the object field
		if (frame instanceof Frame) {
			data.length = 0;
			codecs.forEach(binding => {
				data.push(frame[binding.key]);
			});
		}

		// Mismatch between codecs count (and thus fields) and arguments
		if (codecs.length != data.length) {
			throw new Error("Count mismatch between frame fields and arguments");
		}

		// Compute resulting buffer length
		// Also prepare complex types (strings / buffers)
		let len = 1;
		for (let i = 0; i < codecs.length; ++i) {
			const codec = codecs[i].codec;
			if (codec.prepare) {
				data[i] = codec.prepare(data[i]);
				len += data[i].byteLength + 2;
			} else if (codec.length) {
				len += codec.length;
			} else {
				throw new Error("Codec has neither prepare() method nor length property");
			}
		}

		// Create the buffer with the correct length
		const writer = new BufferWriter(len);
		writer.uint8(frame_type);

		// Write each element into the buffer
		for (let i = 0; i < codecs.length; ++i) {
			const codec = codecs[i].codec;
			const value = data[i];

			if (value instanceof ArrayBuffer) {
				// The value is a buffer built by a prepare() method
				writer.buffer(value);
			} else {
				// The value still need to be encoded by a fixed-length codec
				codec.encode(value, writer);
			}
		}

		return writer.done();
	}
}

/* ================================================================================================================== */
/* Annotations                                                                                                        */
/* ================================================================================================================== */

/**
 * The frame annotation, register the constructor for later decoding and define the Frame#frame_type
 */
function frame(ftype: number) {
	return (target: FrameConstructor) => {
		target.prototype.frame_type = ftype;
		Frame.constructors.set(ftype, target);
	};
}

/**
 * Codec annotation, used to bind a codec to an object property
 */
function codec(codec: Codec<any>) {
	return (target: Frame, key: string) => {
		if (!target.__codecs) target.__codecs = [];
		target.__codecs.push({key: key, codec: codec});
	};
}

// Short versions
const bool = codec(Codecs.bool);
const uint8 = codec(Codecs.uint8);
const uint16 = codec(Codecs.uint16);
const uint32 = codec(Codecs.uint32);
const uint64 = codec(Codecs.uint64);
const str = codec(Codecs.str);
const flags = uint16;
const buf = codec(Codecs.buffer);

/**
 * The specialized sequence number codec, bind the property to a uint16
 * Also define the Frame#sequenced property on the object
 */
function seq(target: Frame, key: string) {
	// Ensure the @seq property is named 'seq'
	if (key != "seq") {
		throw new Error("@seq property must be called 'seq'");
	}

	// Apply the sub-codec
	uint16(target, key);

	// Ensure that the seq field is the first one defined
	if (target.__codecs.length != 1) {
		throw new Error("@seq property must be the first one defined");
	}

	// Tag the frame as sequenced
	target.sequenced = true;
}

/**
 * The specialized channel id codec, bind the property to a uint16
 * Also define the Frame#channel_frame property on the object
 */
function channel(target: Frame, key: string) {
	// Ensure the @channel property is named 'channel'
	if (key != "channel") {
		throw new Error("@channel property must be called 'channel'");
	}

	// Apply the sub-codec
	uint16(target, key);

	// Ensure that the @channel field is defined right after @seq
	if (target.__codecs.length != 2 || target.__codecs[0].key != "seq") {
		throw new Error("@channel property must be defined right after @seq");
	}

	// Tag the frame as a channel frame
	target.channel_frame = true;
}

/* ================================================================================================================== */
/* Frames Definitions                                                                                                 */
/* ================================================================================================================== */

@frame(FrameType.HELLO)
export class HelloFrame extends Frame {
	@uint32  magic: number;
	@str     version: string;
}

@frame(FrameType.HANDSHAKE)
export class HandshakeFrame extends Frame {
	@uint32  magic: number;
	@str     version: string;
	@uint64  sockid: UInt64;
}

@frame(FrameType.RESUME)
export class ResumeFrame extends Frame {
	@uint64  sockid: number;
	@uint16  last_seq: number;
}

@frame(FrameType.SYNC)
export class SyncFrame extends Frame {
	@uint16  last_seq: number;
}

@frame(FrameType.ACK)
export class AckFrame extends Frame {
	@uint16  last_seq: number;
}

@frame(FrameType.BYE)
export class ByeFrame extends Frame {
	@uint16  code: number;
	@str     message: string;
}

@frame(FrameType.IGNORE)
export class IgnoreFrame extends Frame {}

@frame(FrameType.PING)
export class PingFrame extends Frame {}

@frame(FrameType.PONG)
export class PongFrame extends Frame {}

@frame(FrameType.REQUEST_ACK)
export class RequestAckFrame extends Frame {}

@frame(FrameType.OPEN)
export class OpenFrame extends Frame {
	@seq     seq: number;
	@uint16  sender_channel: number;
	@str     channel_type: string;
	@str     token: string;
	@uint16  parent_channel: number;
}

@frame(FrameType.OPEN_SUCCESS)
export class OpenSuccessFrame extends Frame {
	@seq     seq: number;
	@uint16  recipient_channel: number;
	@uint16  sender_channel: number;
}

@frame(FrameType.OPEN_FAILURE)
export class OpenFailureFrame extends Frame {
	@seq     seq: number;
	@uint16  recipient_channel: number;
	@uint16  code: number;
	@str     message: string;
}

@frame(FrameType.RESET)
export class ResetFrame extends Frame {
	@uint16  sender_channel: number;
}

@frame(FrameType.MESSAGE)
export class MessageFrame extends Frame {
	@seq      seq: number;
	@channel  channel: number;
	@str      message: string;
	@flags    flags: number;
	@buf      payload: ArrayBuffer;
}

@frame(FrameType.REQUEST)
export class RequestFrame extends Frame {
	@seq      seq: number;
	@channel  channel: number;
	@str      message: string;
	@uint16   request: number;
	@flags    flags: number;
	@buf      payload: ArrayBuffer;
}

@frame(FrameType.SUCCESS)
export class SuccessFrame extends Frame {
	@seq      seq: number;
	@channel  channel: number;
	@uint16   request: number;
	@flags    flags: number;
	@buf      payload: ArrayBuffer;
}

@frame(FrameType.FAILURE)
export class FailureFrame extends Frame {
	@seq      seq: number;
	@channel  channel: number;
	@uint16   request: number;
	@uint16   code: number;
	@str      message: string;
}

@frame(FrameType.CLOSE)
export class CloseFrame extends Frame {
	@seq      seq: number;
	@channel  channel: number;
	@uint16   code: number;
	@str      message: string;
}
