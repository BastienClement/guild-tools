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

	// Peek data
	peekUint8() { return this.data.getUint8(this._offset); }
	peekInt8() { return this.data.getInt8(this._offset); }
	peekUint16(le?: boolean) { return this.data.getUint16(this._offset, le); }
	peekInt16(le?: boolean) { return this.data.getInt16(this._offset, le); }
	peekUint32(le?: boolean) { return this.data.getUint32(this._offset, le); }
	peekInt32(le?: boolean) { return this.data.getInt32(this._offset, le); }
	peekFloat32(le?: boolean) { return this.data.getFloat32(this._offset, le); }
	peekFloat64(le?: boolean) { return this.data.getFloat64(this._offset, le); }

	// Write data
	writeUint8(value: number) { return this.data.setUint8(value, this.skip(1)); }
	writeInt8(value: number) { return this.data.setInt8(value, this.skip(1)); }
	writeUint16(value: number, le?: boolean) { return this.data.setUint16(this.skip(2), value, le); }
	writeInt16(value: number, le?: boolean) { return this.data.setInt16(this.skip(2), value, le); }
	writeUint32(value: number, le?: boolean) { return this.data.setUint32(this.skip(4), value, le); }
	writeInt32(value: number, le?: boolean) { return this.data.setInt32(this.skip(4), value, le); }
	writeFloat32(value: number, le?: boolean) { return this.data.setFloat32(this.skip(4), value, le); }
	writeFloat64(value: number, le?: boolean) { return this.data.setFloat64(this.skip(8), value, le); }

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
