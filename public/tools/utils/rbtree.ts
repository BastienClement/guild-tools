
class RBNode<K, V> {
	public static nil = new RBNode<any, any>(void 0, void 0);

	public key: K;
	public value: V;
	public red: boolean;
	public nil: boolean;
	public left: RBNode<K, V>;
	public right: RBNode<K, V>;

	public constructor(k: K, v: V) {
		if (k === void 0) {
			this.nil = true;
			this.red = false;
		} else {
			this.key = k;
			this.value = v;
			this.left = RBNode.nil;
			this.right = RBNode.nil;
			this.red = true;
		}
	}
}

export class RBTree<K, V> {
	private root: RBNode<K, V> = RBNode.nil;

	/***************************************************************************
	 *  Standard BST search.
	 ***************************************************************************/

	/**
	 * Returns the value associated with the given key.
	 * @param key   the key
	 */
	public 'get'(key: K): V {
		let h = this.root;
		while (!h.nil) {
			if (key == h.key)     return h.value;
			else if (key < h.key) h = h.left;
			else                  h = h.right;
		}
		return null;
	}

	/**
	 * Does this symbol table contain the given key?
	 * @param key   the key
	 */
	public contains(key: K): boolean {
		return this.get(key) !== null;
	}

	public clear(): void {
		this.root = RBNode.nil;
	}

	/***************************************************************************
	 *  Red-black tree insertion.
	 ***************************************************************************/

	public put(key: K, value: V): void {
		this.root = this.insert(this.root, key, value);
		this.root.red = false;
	}

	private insert(h: RBNode<K, V>, key: K, val: V): RBNode<K, V> {
		if (h.nil) return new RBNode(key, val);

		if (key == h.key)     h.value = val;
		else if (key < h.key) h.left  = this.insert(h.left, key, val);
		else                  h.right = this.insert(h.right, key, val);

		if (h.right.red && !h.left.red)      h = this.rotateLeft(h);
		if (h.left.red  &&  h.left.left.red) h = this.rotateRight(h);
		if (h.left.red  &&  h.right.red)     this.flipColors(h);

		return h;
	}

	/***************************************************************************
	 *  Red-black tree deletion.
	 ***************************************************************************/

	/**
	 * Removes the smallest key and associated value from the symbol table.
	 */
	private deleteMin() {
		if (this.root.nil) throw new RangeError("BST underflow");

		if (!this.root.left.red && !this.root.right.red) {
			this.root.red = true;
		}

		this.root = this.deleteMinNode(this.root);
		if (!this.root.nil) this.root.red = false;
	}

	// Delete the key-value pair with the minimum key rooted at h
	private deleteMinNode(h: RBNode<K, V>): RBNode<K, V> {
		if (h.left.nil) {
			return RBNode.nil;
		}

		if (!h.left.red && !h.left.left.red) {
			h = this.moveRedLeft(h);
		}

		h.left = this.deleteMinNode(h.left);
		return this.balance(h);
	}

	/**
	 * Removes the largest key and associated value from the symbol table.
	 */
	private deleteMax() {
		if (this.root.nil) throw new RangeError("BST underflow");

		if (!this.root.left.red && !this.root.right.red) {
			this.root.red = true;
		}

		this.root = this.deleteMaxNode(this.root);
		if (!this.root.nil) this.root.red = false;
	}

	// Delete the key-value pair with the minimum key rooted at h
	private deleteMaxNode(h: RBNode<K, V>): RBNode<K, V> {
		if (h.left.red) {
			h = this.rotateRight(h);
		}

		if (h.right.nil) {
			return RBNode.nil;
		}

		if (!h.right.red && !h.right.left.red) {
			h = this.moveRedRight(h);
		}

		h.right = this.deleteMaxNode(h.right);
		return this.balance(h);
	}

	/**
	 * Removes the specified key and its associated value from this symbol table
	 * (if the key is in this symbol table).
	 * @param  key the key
	 */
	public 'delete'(key: K) {
		if (!this.contains(key)) return;

		if (!this.root.left.red && !this.root.right.red) {
			this.root.red = true;
		}

		this.root = this.deleteNode(this.root, key);
		if (!this.root.nil) this.root.red = false;
	}

	// Delete the key-value pair with the given key rooted at h
	private deleteNode(h: RBNode<K, V>, key: K): RBNode<K, V> {
		if (key < h.key) {
			if (!h.left.red && !h.left.left.red)
				h = this.moveRedLeft(h);
			h.left = this.deleteNode(h.left, key);
		} else {
			if (h.left.red)
				h = this.rotateRight(h);
			if (key == h.key && h.right.nil)
				return RBNode.nil;
			if (!h.right.red && !h.right.left.red)
				h = this.moveRedRight(h);
			if (key == h.key) {
				let x = this.minNode(h.right);
				h.key = x.key;
				h.value = x.value;
				h.right = this.deleteMinNode(h.right);
			} else {
				h.right = this.deleteNode(h.right, key);
			}
		}
		return this.balance(h);
	}

	/***************************************************************************
	 *  Red-black tree helper functions.
	 ***************************************************************************/

	// Make a left-leaning link lean to the right
	private rotateRight(h: RBNode<K, V>): RBNode<K, V> {
		let x = h.left;
		h.left = x.right;
		x.right = h;
		x.red = x.right.red;
		x.right.red = true;
		return x;
	}

	// Make a right-leaning link lean to the left
	private rotateLeft(h: RBNode<K, V>): RBNode<K, V> {
		let x= h.right;
		h.right = x.left;
		x.left = h;
		x.red = x.left.red;
		x.left.red = true;
		return x;
	}

	// Flip the colors of a node and its two children
	private flipColors(h: RBNode<K, V>) {
		h.red = !h.red;
		h.left.red = !h.left.red;
		h.right.red = !h.right.red;
	}

	// Assuming that h is red and both h.left and h.left.left
	// are black, make h.left or one of its children red.
	private moveRedLeft(h: RBNode<K, V>): RBNode<K, V> {
		this.flipColors(h);
		if (h.right.left.red) {
			h.right = this.rotateRight(h.right);
			h = this.rotateLeft(h);
			this.flipColors(h);
		}
		return h;
	}

	// Assuming that h is red and both h.right and h.right.left
	// are black, make h.right or one of its children red.
	private moveRedRight(h: RBNode<K, V>): RBNode<K, V> {
		this.flipColors(h);
		if (h.left.left.red) {
			h = this.rotateRight(h);
			this.flipColors(h);
		}
		return h;
	}

	// Restore red-black tree invariant
	private balance(h: RBNode<K, V>): RBNode<K, V> {
		if (h.right.red) h = this.rotateLeft(h);
		if (h.left.red && h.left.left.red) h = this.rotateRight(h);
		if (h.left.red && h.right.red) this.flipColors(h);
		return h;
	}

	/***************************************************************************
	 *  Ordered symbol table methods.
	 ***************************************************************************/

	/**
	 * Returns the smallest key in the symbol table.
	 */
	private min(): K {
		return this.minNode(this.root).key;
	}

	// the smallest key in subtree rooted at x
	private minNode(x: RBNode<K, V>): RBNode<K, V> {
		if (x.left.nil) return x;
		return this.minNode(x.left);
	}

	/**
	 * Returns the largest key in the symbol table.
	 */
	private max(): K {
		return this.maxNode(this.root).key;
	}

	// the largest key in subtree rooted at x
	private maxNode(x: RBNode<K, V>): RBNode<K, V> {
		if (x.right.nil) return x;
		return this.maxNode(x.right);
	}

	/***************************************************************************
	 *  Range count and range search.
	 ***************************************************************************/

	public [Symbol.iterator](): Iterator<[K, V]> {
		return this.search()[Symbol.iterator]();
	}

	public search(lo: K = this.min(), hi: K = this.max()): [K, V][] {
		let queue = <[K, V][]> [];
		this.searchNode(this.root, queue, lo, hi);
		return queue;
	}

	private searchNode(x: RBNode<K, V>, queue: [K, V][], lo: K, hi: K) {
		if (x.nil) return;
		if (lo < x.key) this.searchNode(x.left, queue, lo, hi);
		if (lo <= x.key && hi >= x.key) queue.push([x.key, x.value]);
		if (hi > x.key) this.searchNode(x.right, queue, lo, hi);
	}
}
