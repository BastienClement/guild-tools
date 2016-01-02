/**
 * A bidirectional map used to define equivalence between two domains.
 * The BiMap can be queried to get the key associated to a given value
 * in addition of querying the value associated to a key.
 */
export class BiMap<K, V> {
	private key_value = new Map<K, V>();
	private value_key = new Map<V, K>();

	/**
	 * Defines a new entry
	 * @param key   The key of the entry
	 * @param value The value of the entry
	 */
	public put(key: K, value: V) {
		this.removeKey(key);
		this.removeValue(value);
		this.key_value.set(key, value);
		this.value_key.set(value, key);
	}

	/**
	 * Retrieves the value associated to the given key
	 * @param key   The key to search for
	 * @returns {V} The value associated to this key, if defined
	 */
	public getValue(key: K) {
		return this.key_value.get(key);
	}

	/**
	 * Retrieves the key associated to the given value
	 * @param value The value to search for
	 * @returns {V} The key associated to this value, if defined
	 */
	public getKey(value: V) {
		return this.value_key.get(value);
	}

	/**
	 * Remove a given key from the BiMap
	 * @param key   The key to remove
	 */
	public removeKey(key: K) {
		if (this.key_value.has(key)) {
			this.remove(key, this.key_value.get(key));
		}
	}

	/**
	 * Remove a given value from the BiMap
	 * @param value The value to remove
	 */
	public removeValue(value: V) {
		if (this.value_key.has(value)) {
			this.remove(this.value_key.get(value), value);
		}
	}

	/**
	 * Removes a specific pair from the BiMap.
	 * This function does not check that the key is actually
	 * associated with the value.
	 *
	 * @param key   The key to remove
	 * @param value The value to remove
	 */
	private remove(key: K, value: V) {
		this.key_value.delete(key);
		this.value_key.delete(value);
	}

	/**
	 * Constructs a BiMap from an object.
	 */
	public static fromObject<V>(obj: { [key: string]: V; }): BiMap<string, V> {
		let bimap = new BiMap<string, V>();
		for (let key in obj) {
			bimap.put(key, obj[key]);
		}
		return bimap;
	}
}
