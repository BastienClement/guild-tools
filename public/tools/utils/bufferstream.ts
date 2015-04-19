// <reference path="../defs/encoding.d.ts" />

const decoder = new TextDecoder("utf-8");

class BufferStream {
	// Internal buffer
	private buf: ArrayBuffer;

	// DataView object attached to the buffer
	private data: DataView;

	// Current stream offset in the buffer
	private _offset: number = 0;

	constructor(buf: ArrayBuffer);
	constructor(size: number);
	constructor(buf: any) {
		this.buf = (typeof buf === "number") ? new ArrayBuffer(buf) : buf;
		this.data = new DataView(this.buf);
	}

	// Buffer navigation
	offset() {
		return this._offset;
	}

	seek(target: number) {
		this._offset = target;
	}

	skip(bytes: number) {
		const old = this._offset;
		this._offset += bytes;
		return old;
	}

	rewind(bytes: number) {
		return this.skip(-bytes);
	}

	// Read data
	readUint8() { return this.data.getUint8(this.skip(1)); }
	readInt8() { return this.data.getInt8(this.skip(1)); }
	readUint16(le?: boolean) { return this.data.getUint16(this.skip(2), le); }
	readInt16(le?: boolean) { return this.data.getInt16(this.skip(2), le); }
	readUint32(le?: boolean) { return this.data.getUint32(this.skip(4), le); }
	readInt32(le?: boolean) { return this.data.getInt32(this.skip(4), le); }
	readFloat32(le?: boolean) { return this.data.getFloat32(this.skip(4), le); }
	readFloat64(le?: boolean) { return this.data.getFloat64(this.skip(8), le); }

	readString(length: number) { return decoder.decode(new Uint8Array(this.buf, this.skip(length), length)); }
	readString8() { return this.readString(this.readUint8()); }
	readString16() { return this.readString(this.readUint16()); }
	readString32() { return this.readString(this.readUint32()); }

	readBuffer(length: number = -1) {
		if (length < 0) length = this.buf.byteLength - this._offset;
		if (length == 0) return null;

		const buffer = new ArrayBuffer(length);
		new Uint8Array(buffer).set(new Uint8Array(this.buf, this.skip(length), length));

		return buffer;
	}

	readBoolean() { return this.readUint8() != 0; }

	// Write data
	writeUint8(value: number) { return this.data.setUint8(value, this.skip(1)); }
	writeInt8(value: number) { return this.data.setInt8(value, this.skip(1)); }
	writeUint16(value: number, le?: boolean) { return this.data.setUint16(this.skip(2), value, le); }
	writeInt16(value: number, le?: boolean) { return this.data.setInt16(this.skip(2), value, le); }
	writeUint32(value: number, le?: boolean) { return this.data.setUint32(this.skip(4), value, le); }
	writeInt32(value: number, le?: boolean) { return this.data.setInt32(this.skip(4), value, le); }
	writeFloat32(value: number, le?: boolean) { return this.data.setFloat32(this.skip(4), value, le); }
	writeFloat64(value: number, le?: boolean) { return this.data.setFloat64(this.skip(8), value, le); }

	writeBuffer(buffer: ArrayBuffer) {
		const length = buffer.byteLength;
		new Uint8Array(this.buf, this.skip(length), length).set(new Uint8Array(buffer));
	}

	writeBoolean(value: boolean) { this.writeUint8(value ? 1 : 0); }

	// Extract buffer
	buffer() {
		return this.buf;
	}

	// Compact buffer to fit last write
	compact() {
		if (this._offset != this.buf.byteLength) {
			const new_buf = new ArrayBuffer(this._offset);
			new Uint8Array(new_buf).set(new Uint8Array(this.buf, 0, this._offset));
			this.buf = new_buf;
		}

		return this.buf;
	}
}

export = BufferStream
