package utils

import scala.collection.mutable

object BiMap {
	/**
	  * Creates an empty bidirectionnal Map.
	  */
	def empty[K, V]: BiMap[K, V] = new BiMap()

	/**
	  * Creates an empty bidirectionnal Map with types T, V unrelated.
	  * If T and V are unrelated, the compiler is able to distinguish between key-based
	  * and value-based accesses.
	  */
	def unrelated[K, V]: BiMap[K, V] with Unrelated[K, V] = new BiMap[K, V] with Unrelated[K, V]

	object Unrelated {
		private[BiMap] trait MethodDistinctor
		implicit object MethodDistinctor extends MethodDistinctor
	}

	/**
	  * Adds overloaded method for value-based operations.
	  */
	trait Unrelated[K, V] {
		this: BiMap[K, V] =>

		def put(vk: (V, K))(implicit d: Unrelated.MethodDistinctor) = {
			key_value += vk.swap
			value_key += vk
		}

		def remove(value: V)(implicit d: Unrelated.MethodDistinctor) = removeValue(value)
		def get(value: V)(implicit d: Unrelated.MethodDistinctor) = getKey(value)
		def contains(value: V)(implicit d: Unrelated.MethodDistinctor) = containsValue(value)
	}
}

/**
  * Bidirectionnal map
  */
class BiMap[K, V] private () {
	/**
	  * The internal mapping of keys to values
	  */
	private val key_value = mutable.Map.empty[K, V]

	/**
	  * The interval mapping of values to keys
	  */
	private val value_key = mutable.Map.empty[V, K]

	/**
	  * Adds a new binding to the BiMap.
	  */
	def put(kv: (K, V)) = {
		val (key, value) = kv
		key_value.put(key, value).foreach(old_value => value_key.remove(old_value))
		value_key.put(value, key).foreach(old_key => key_value.remove(old_key))
	}

	/**
	  * Removes a key and the associated value from the BiMap.
	  */
	def remove(key: K) = for (value <- key_value.get(key)) removeBinding(key, value)

	/**
	  * Removes a value and the associated key from the BiMap.
	  */
	def removeValue(value: V) = for (key <- value_key.get(value)) removeBinding(key, value)

	/**
	  * Removes a key and value from the BiMap.
	  * Must be called with an existing bindings.
	  */
	private def removeBinding(key: K, value: V) = {
		key_value.remove(key)
		value_key.remove(value)
	}

	/**
	  * Check if a given key is present in the map.
	  */
	def contains(key: K): Boolean = key_value.contains(key)

	/**
	  * Check if a given value is present in the map.
	  */
	def containsValue(value: V): Boolean = value_key.contains(value)

	/**
	  * Returns the value bound to the key.
	  */
	def get(key: K): Option[V] = key_value.get(key)

	/**
	  * Returns the key bound to the given value.
	  */
	def getKey(value: V): Option[K] = value_key.get(value)

	/**
	  * Returns an iterator over the BiMap bindings
	  */
	def iterator: Iterator[(K, V)] = key_value.iterator

	/**
	  * Returns the size of the BiMap
	  */
	def size = key_value.size
}
