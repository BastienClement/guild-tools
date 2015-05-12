import { Queue } from "utils/queue";

export class NumberPool {
	private max: number = 0;
	private allocated: Set<number> = new Set<number>();
	private released: Array<number> = [];

	constructor(private limit: number = 0xFFFF) {
	}

	canAllocate(): boolean {
		return this.released.length > 0 || this.max < this.limit;
	}

	allocate(): number {
		let n: number;

		if (this.released.length > 0) {
			n = this.released.pop();
		} else if (this.max < this.limit) {
			n = ++this.max;
		} else {
			throw new Error("Unable to allocate next number (limit reached)");
		}

		this.allocated.add(n);
		return n;
	}

	release(n: number): void {
		if (this.allocated.has(n)) {
			this.allocated.delete(n);
			this.released.push(n);
		}
	}

	clear(): void {
		this.max = 1;
		this.allocated.clear();
		this.released.length = 0;
	}
}
