export class UInt64 {
	constructor(public hi: number, public lo: number) {}
	static Zero: UInt64 = new UInt64(0, 0);
}

class BufferStream {
	protected data: DataView;
	private length: number;
	private offset: number = 0;

	constructor(buffer: ArrayBuffer) {
		this.data = new DataView(buffer);
		this.length = buffer.byteLength;
	}

	skip(length: number): number {
		const current_offset = this.offset;
		this.offset += length;
		return current_offset;
	}

	tell(): number {
		return this.offset;
	}

	seek(pos: number): void {
		this.offset = (pos < 0) ? this.length + pos : pos;
	}
}

export class BufferReader extends BufferStream {
	bool(): boolean {
		return this.uint8() != 0;
	}

	uint8(): number {
		return this.data.getUint8(this.skip(1));
	}

	uint16(): number {
		return this.data.getUint16(this.skip(2), false);
	}

	uint32(): number {
		return this.data.getUint32(this.skip(4), false);
	}

	uint64(): UInt64 {
		return new UInt64(this.uint32(), this.uint32());
	}

	buffer(): ArrayBuffer {
		const len = this.uint16();
		const begin = this.skip(len);
		return this.data.buffer.slice(begin, begin + len);
	}
}

export class BufferWriter extends BufferStream {
	constructor(size: number) {
		super(new ArrayBuffer(size));
	}

	bool(v: boolean) {
		this.uint8(v ? 1 : 0);
	}

	uint8(v: number) {
		this.data.setUint8(this.skip(1), v);
	}

	uint16(v: number) {
		this.data.setUint16(this.skip(2), v, false);
	}

	uint32(v: number) {
		this.data.setUint32(this.skip(4), v, false);
	}

	uint64(v: UInt64) {
		this.uint32(v.hi);
		this.uint32(v.lo);
	}

	buffer(v: ArrayBuffer) {
		const len = v.byteLength;
		this.uint16(len);
		new Uint8Array(this.data.buffer, this.skip(len), len).set(new Uint8Array(v));
	}

	done(): ArrayBuffer {
		return this.data.buffer;
	}
}
