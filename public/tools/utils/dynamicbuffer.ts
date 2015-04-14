class DynamicBuffer {
	private buffer: Uint8Array;
	private start: number = 0;
	private length: number = 0;

	constructor(private size: number) {
		this.buffer = new Uint8Array(this.size);
	}

	available() {
		return this.size - this.length;
	}

	write(buf: ArrayBuffer) {
		const len = buf.byteLength;

		if (len > this.available()) {
			throw new Error("Not enough space in buffer")
		}

		// Compute the offset of the first byte written
		const write_start = (this.start + this.length) % this.size;

		if (write_start + len > this.size) {
			// Need to split the buffer
			const split_at = this.size - write_start;
			this.buffer.set(new Uint8Array(buf, 0, split_at), write_start);
			this.buffer.set(new Uint8Array(buf, split_at), 0);
		} else {
			this.buffer.set(new Uint8Array(buf), write_start);
		}

		this.length += len;
	}

	private effectiveBytes(b: number) {
		return Math.min(this.length, b);
	}

	extract(to: ArrayBuffer, offset: number = 0, len: number = this.length) {
		const effective_len = this.effectiveBytes(len);
		const target = 	new Uint8Array(to, offset);

		if (this.start + effective_len > this.size) {
			// Split read
			const split_at = this.size - this.start;
			target.set(new Uint8Array(this.buffer, this.start, split_at));
			target.set(new Uint8Array(this.buffer, split_at, effective_len - split_at), split_at);
		} else {
			target.set(new Uint8Array(this.buffer, this.start, effective_len));
		}

		this.length -= effective_len;
		this.start = (this.start + effective_len) % this.size;

		return effective_len;
	}

	read(bytes: number = this.length) {
		const effective_len = this.effectiveBytes(bytes);
		const buf = new ArrayBuffer(effective_len)
		this.extract(buf, 0, effective_len);
		return buf;
	}
}

export = DynamicBuffer;
