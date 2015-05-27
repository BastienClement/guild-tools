import { BufferReader, BufferWriter, UInt64 } from "gtp3/bufferstream";

export const UTF8Decoder: TextDecoder = new TextDecoder("utf-8");
export const UTF8Encoder: TextEncoder = new TextEncoder("utf-8");

export interface Codec<T> {
	decode: (buffer: BufferReader) => T;
	encode?: (data: T, buffer: BufferWriter) => void;
	prepare?: (data: T) => ArrayBuffer;
	length?: number;
}

export const bool: Codec<boolean> = {
	decode: (buffer: BufferReader): boolean => buffer.bool(),
	encode: (data: boolean, buffer: BufferWriter) => buffer.bool(data),
	length: 1
};

export const uint8: Codec<number> = {
	decode: (buffer: BufferReader): number => buffer.uint8(),
	encode: (data: number, buffer: BufferWriter) => buffer.uint8(data),
	length: 1
};

export const uint16: Codec<number> = {
	decode: (buffer: BufferReader): number => buffer.uint16(),
	encode: (data: number, buffer: BufferWriter) => buffer.uint16(data),
	length: 2
};

export const uint32: Codec<number> = {
	decode: (buffer: BufferReader): number => buffer.uint32(),
	encode: (data: number, buffer: BufferWriter) => buffer.uint32(data),
	length: 4
};

export const uint64: Codec<UInt64> = {
	decode: (buffer: BufferReader): UInt64 => buffer.uint64(),
	encode: (data: UInt64, buffer: BufferWriter) => buffer.uint64(data),
	length: 8
};

export const str: Codec<string> = {
	decode: (buffer: BufferReader): string => UTF8Decoder.decode(new Uint8Array(buffer.buffer())),
	prepare: (data: string): ArrayBuffer => UTF8Encoder.encode(data).buffer
};

export const buffer: Codec<ArrayBuffer> = {
	decode: (buffer: BufferReader): ArrayBuffer => buffer.buffer(),
	prepare: (data: ArrayBuffer): ArrayBuffer => data
};
